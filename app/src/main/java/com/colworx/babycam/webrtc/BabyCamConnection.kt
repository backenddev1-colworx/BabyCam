package com.colworx.babycam.webrtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.util.Log
import com.colworx.babycam.audio.LullabyPlayer
import com.colworx.babycam.audio.CryAudioBridge
import com.colworx.babycam.data.AppPreferences
import com.colworx.babycam.service.CryNotifier
import com.colworx.babycam.signaling.SignalMessage
import com.colworx.babycam.signaling.SignalingClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnRole { BABY, PARENT }

class BabyCamConnection(
    private val context: Context,
    private val role: ConnRole,
    private val room: String,
    private val onRemoteVideo: (VideoTrack) -> Unit,
    private val onLocalVideo: (VideoTrack) -> Unit = {},
    private val onRemoteAudio: (AudioTrack) -> Unit = {},
    private val onState: (PeerConnection.IceConnectionState) -> Unit = {},
    private val onSignalingUp: (Boolean) -> Unit = {},
    private val onBatteryUpdate: (Int) -> Unit = {},
    private val onCryAlert: () -> Unit = {},
    private val onTorchState: (Boolean) -> Unit = {},
    private val onControlState: (SessionControlState) -> Unit = {},
) : WebRtcSession.Listener {

    private val selfId = UUID.randomUUID().toString().take(8)
    private val session = WebRtcSession(context, this)
    private val signaling = SignalingClient(selfId)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stopped = AtomicBoolean(false)
    private val syncGate = SessionSyncGate()
    private val syncTracker = ParentSyncTracker()
    private val commandTracker = ParentCommandTracker()
    private val controlRevisionGate = ControlRevisionGate()
    private val controlMutex = Mutex()
    private val reconnectPending = AtomicBoolean(false)
    private val offerWorkerRunning = AtomicBoolean(false)
    private val offerRequested = AtomicBoolean(false)
    private val iceRestartRequested = AtomicBoolean(false)

    @Volatile private var currentSyncId: String? = null
    @Volatile private var leaseDeadlineMs = 0L
    @Volatile private var hasAcceptedParentSync = false
    @Volatile private var desiredState = SessionControlState()
    @Volatile private var actualState = SessionControlState()

    private var batteryReceiver: BroadcastReceiver? = null
    private var leaseJob: Job? = null
    private var heartbeatJob: Job? = null
    private var syncRetryJob: Job? = null
    private var torchMaintenanceJob: Job? = null
    private val disconnectAcknowledged = CompletableDeferred<Unit>()
    private val batteryPublisher = BatteryPublisher { percentage ->
        if (hasAcceptedParentSync && !stopped.get()) sendBattery(percentage)
    }

    val eglContext get() = session.eglBase.eglBaseContext
    val localVideoTrack get() = session.localVideoTrack

    fun start() {
        scope.launch {
            if (stopped.get()) return@launch
            session.resolveCloudflareIceServers()
            session.initialize()
            session.startAudio()
            if (role == ConnRole.BABY) {
                session.startCamera(useFront = false)
                session.localVideoTrack?.let(onLocalVideo)
                    ?: Log.e(TAG, "Baby: camera capturer unavailable")
                session.addParentVideoReceiveSlot()
                registerBatteryReceiver()
            }
            signaling.connect(
                room = room,
                onMessage = ::onSignal,
                onReady = ::onSignalingReady,
                onState = ::onSignalingState,
            )
        }
    }

    private fun onSignalingReady(reconnected: Boolean) {
        if (stopped.get()) return
        Log.d(TAG, "Signaling ready, role=$role reconnect=$reconnected")
        if (role == ConnRole.PARENT) {
            beginParentSync()
        }
    }

    private fun onSignalingState(up: Boolean) {
        if (stopped.get()) return
        onSignalingUp(up)
        if (!up && role == ConnRole.PARENT) {
            heartbeatJob?.cancel()
            session.setLocalAudioEnabled(false)
            session.setCameraStandby(true)
            onControlState(actualState.copy(parentCamera = false, parentTalk = false))
        }
    }

    private fun beginParentSync() {
        val syncId = UUID.randomUUID().toString()
        currentSyncId = syncId
        syncTracker.begin(syncId)
        signaling.send("session_sync", syncId)
        syncRetryJob?.cancel()
        syncRetryJob = scope.launch {
            while (isActive && !stopped.get() && syncTracker.shouldRetry(syncId)) {
                delay(SYNC_RETRY_MS)
                if (syncTracker.shouldRetry(syncId)) signaling.send("session_sync", syncId)
            }
        }
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && !stopped.get() && currentSyncId == syncId) {
                signaling.send("control_lease", syncId)
                delay(LEASE_HEARTBEAT_MS)
            }
        }
    }

    private fun onSignal(msg: SignalMessage) {
        if (msg.type == "parent_disconnect_ack" && role == ConnRole.PARENT) {
            if (msg.payload == currentSyncId && !disconnectAcknowledged.isCompleted) {
                disconnectAcknowledged.complete(Unit)
            }
            return
        }
        if (stopped.get()) return
        when (msg.type) {
            "presence_ping" -> if (role == ConnRole.BABY && msg.payload.isNotBlank()) {
                signaling.send("presence_pong", msg.payload)
            }
            "session_sync" -> if (role == ConnRole.BABY) acceptParentSync(msg.payload)
            "session_ready" -> if (role == ConnRole.PARENT) acceptSessionReady(msg.payload)
            "control_lease" -> if (role == ConnRole.BABY && msg.payload == currentSyncId) {
                refreshLease()
            }
            "parent_disconnect" -> if (role == ConnRole.BABY && msg.payload == currentSyncId) {
                applyFailSafe("parent disconnect")
                signaling.send("parent_disconnect_ack", msg.payload)
            }
            "control" -> if (role == ConnRole.BABY) applyControl(msg.payload)
            "state_ack" -> if (role == ConnRole.PARENT) receiveStateAck(msg.payload)
            "state_snapshot" -> if (role == ConnRole.PARENT) receiveStateSnapshot(msg.payload)
            "offer" -> if (role == ConnRole.PARENT) receiveOffer(msg.payload)
            "answer" -> if (role == ConnRole.BABY) receiveAnswer(msg.payload)
            "ice" -> receiveIce(msg.payload)
            "switch_camera" -> if (role == ConnRole.BABY) session.switchCamera()
            "battery" -> if (role == ConnRole.PARENT) {
                msg.payload.toIntOrNull()?.let(onBatteryUpdate)
            }
            "cry" -> if (role == ConnRole.PARENT) {
                scope.launch {
                    if (AppPreferences(context).notificationsEnabled.first()) {
                        CryNotifier.postCryAlert(context)
                    }
                }
                onCryAlert()
            }
        }
    }

    private fun acceptParentSync(syncId: String) {
        if (syncId.isBlank()) return
        signaling.send("session_ready", syncId)
        if (!syncGate.accept(syncId)) return
        controlRevisionGate.reset()
        // Guarantee the audio sender has no track before we create the new offer. If a previous
        // session's fail-safe cleanup was interrupted by an ISE, the disposed track can stay on
        // the sender and audio leaks to the parent even though both sides report mic OFF.
        session.forceResetAudioSender()
        currentSyncId = syncId
        hasAcceptedParentSync = true
        refreshLease()
        sendStateSnapshot()
        batteryPublisher.forceCurrent()
        requestBabyOffer()
    }

    private fun acceptSessionReady(syncId: String) {
        if (syncTracker.acknowledge(syncId)) {
            syncRetryJob?.cancel()
        }
    }

    private fun refreshLease() {
        leaseDeadlineMs = SystemClock.elapsedRealtime() + CONTROL_LEASE_MS
        if (leaseJob?.isActive == true) return
        leaseJob = scope.launch {
            while (isActive && !stopped.get()) {
                delay(LEASE_CHECK_MS)
                if (leaseDeadlineMs != 0L && SystemClock.elapsedRealtime() >= leaseDeadlineMs) {
                    leaseDeadlineMs = 0L
                    applyFailSafe("control lease expired")
                }
            }
        }
    }

    private fun requestBabyOffer(iceRestart: Boolean = false) {
        if (role != ConnRole.BABY || currentSyncId == null || stopped.get()) return
        offerRequested.set(true)
        if (iceRestart) iceRestartRequested.set(true)
        if (!offerWorkerRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (offerRequested.getAndSet(false) && !stopped.get()) {
                    val syncId = currentSyncId ?: break
                    val restart = iceRestartRequested.getAndSet(false)
                    val result = CompletableDeferred<SessionDescription?>()
                    val started = session.createOffer(
                        iceRestart = restart,
                        onCreated = { result.complete(it) },
                        onFailure = { result.complete(null) },
                    )
                    if (!started) {
                        offerRequested.set(true)
                        delay(50)
                        continue
                    }
                    val sdp = result.await() ?: continue
                    if (!stopped.get() && currentSyncId == syncId) {
                        signaling.send("offer", sdpEnvelope(syncId, sdp.description))
                    }
                }
            } finally {
                offerWorkerRunning.set(false)
                if (offerRequested.get() && !stopped.get()) requestBabyOffer()
            }
        }
    }

    private fun receiveOffer(payload: String) {
        val envelope = parseSdpEnvelope(payload) ?: return
        if (envelope.first != currentSyncId) return
        val syncId = envelope.first
        session.setRemoteDescription(
            SessionDescription(SessionDescription.Type.OFFER, envelope.second),
            onSuccess = {
                if (stopped.get() || currentSyncId != syncId) return@setRemoteDescription
                session.attachParentCamera(useFront = true)
                session.createAnswer(
                    onCreated = { sdp ->
                        if (!stopped.get() && currentSyncId == syncId) {
                            signaling.send("answer", sdpEnvelope(syncId, sdp.description))
                            replayDesiredState()
                        }
                    },
                )
            },
        )
    }

    private fun receiveAnswer(payload: String) {
        val envelope = parseSdpEnvelope(payload) ?: return
        if (envelope.first != currentSyncId) return
        session.setRemoteDescription(
            SessionDescription(SessionDescription.Type.ANSWER, envelope.second),
        )
    }

    private fun applyControl(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val syncId = json.optString("syncId")
        val commandId = json.optString("commandId")
        val name = json.optString("name")
        val enabled = json.optBoolean("enabled")
        val revision = json.optLong("revision")
        if (syncId != currentSyncId || commandId.isBlank() || revision <= 0L) return

        scope.launch {
            controlMutex.withLock {
                if (syncId != currentSyncId || !controlRevisionGate.accept(name, revision)) {
                    return@withLock
                }
                refreshLease()
                if (name == CONTROL_CRY) {
                    AppPreferences(context).setCryDetectionEnabled(enabled)
                    actualState = applyControlResult(actualState, name, enabled, onControlState)
                    sendStateAck(commandId, revision, name, enabled)
                    return@withLock
                }

                val actual = when (name) {
                    CONTROL_CAMERA -> session.setCameraStandby(!enabled)
                    CONTROL_MICROPHONE -> setBabyMicrophone(enabled)
                    CONTROL_TORCH -> {
                        val torchActual = controlTorch(enabled)
                        maintainTorch(torchActual)
                        torchActual
                    }
                    CONTROL_LULLABY -> {
                        if (enabled) {
                            LullabyPlayer.play(LullabyPlayer.Sound.BELL, context.applicationContext)
                        } else {
                            LullabyPlayer.stop()
                        }
                        enabled
                    }
                    CONTROL_PARENT_CAMERA -> enabled
                    CONTROL_PARENT_TALK -> {
                        session.setRemoteAudioPlaybackActive(enabled)
                        enabled
                    }
                    CONTROL_VIDEO_SAVER -> {
                        if (enabled) session.changeCaptureFormat(640, 480, 15)
                        else session.changeCaptureFormat(1280, 720, 30)
                        enabled
                    }
                    else -> return@withLock
                }
                actualState = applyControlResult(actualState, name, actual, onControlState)
                sendStateAck(commandId, revision, name, actual)
            }
        }
    }

    private fun sendControl(name: String, enabled: Boolean) {
        val syncId = currentSyncId ?: return
        val revision = commandTracker.next(name)
        val payload = JSONObject()
            .put("syncId", syncId)
            .put("commandId", UUID.randomUUID().toString())
            .put("revision", revision)
            .put("name", name)
            .put("enabled", enabled)
            .toString()
        signaling.send("control", payload)
    }

    private fun replayDesiredState() {
        if (role != ConnRole.PARENT) return
        desiredState.commands().forEach { (name, enabled) -> sendControl(name, enabled) }
        val parentTalkActual = session.setLocalAudioEnabled(desiredState.parentTalk)
        session.setRemoteAudioPlaybackActive(desiredState.microphone)
        val cameraTransition = session.setCameraStandby(!desiredState.parentCamera)
        val parentCameraActual = cameraActualState(desiredState.parentCamera, cameraTransition)
        actualState = actualState.copy(
            parentCamera = parentCameraActual,
            parentTalk = parentTalkActual,
        )
        onControlState(actualState)
    }

    private fun receiveStateAck(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        if (json.optString("syncId") != currentSyncId) return
        val name = json.optString("name")
        val revision = json.optLong("revision")
        if (!commandTracker.acceptAck(name, revision)) return
        val enabled = json.optBoolean("enabled")
        if (name == CONTROL_MICROPHONE) session.setRemoteAudioPlaybackActive(enabled)
        actualState = actualState.withControl(name, enabled)
        if (name == CONTROL_TORCH) onTorchState(enabled)
        onControlState(actualState)
    }

    private fun receiveStateSnapshot(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        if (json.optString("syncId") != currentSyncId) return
        acceptSessionReady(json.optString("syncId"))
        actualState = stateFromJson(json)
        session.setRemoteAudioPlaybackActive(actualState.microphone)
        onTorchState(actualState.torch)
        onControlState(actualState)
    }

    private fun sendStateAck(commandId: String, revision: Long, name: String, enabled: Boolean) {
        val syncId = currentSyncId ?: return
        signaling.send(
            "state_ack",
            JSONObject()
                .put("syncId", syncId)
                .put("commandId", commandId)
                .put("revision", revision)
                .put("name", name)
                .put("enabled", enabled)
                .toString(),
        )
    }

    private fun sendStateSnapshot() {
        val syncId = currentSyncId ?: return
        signaling.send("state_snapshot", stateToJson(actualState).put("syncId", syncId).toString())
    }

    private fun applyFailSafe(reason: String) {
        if (role != ConnRole.BABY) return
        Log.w(TAG, "Applying fail-safe OFF: $reason")
        torchMaintenanceJob?.cancel()
        torchMaintenanceJob = null
        session.setCameraStandby(true)
        setBabyMicrophone(false)
        controlTorch(false)
        LullabyPlayer.stop()
        scope.launch { AppPreferences(context).setCryDetectionEnabled(false) }
        actualState = actualState.failSafeOff()
        onControlState(actualState)
        sendStateSnapshot()
    }

    private fun registerBatteryReceiver() {
        if (role != ConnRole.BABY || batteryReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = observeBattery(intent)
        }
        batteryReceiver = receiver
        context.applicationContext
            .registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let(::observeBattery)
    }

    private fun observeBattery(intent: Intent) {
        batteryPublisher.observe(
            intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        )
    }

    fun setTalking(on: Boolean) {
        if (role != ConnRole.PARENT) return
        desiredState = desiredState.copy(parentTalk = on)
        val actual = session.setLocalAudioEnabled(on)
        actualState = actualState.copy(parentTalk = actual)
        onControlState(actualState)
        sendControl(CONTROL_PARENT_TALK, actual)
    }

    fun switchCamera() = session.switchCamera()
    fun requestRemoteCameraSwitch() = signaling.send("switch_camera", "")
    fun sendLullaby(sound: String) = updateDesired(CONTROL_LULLABY, sound != "stop")
    fun setVideoEnabled(enabled: Boolean) = setRemoteCamera(enabled)
    fun sendBattery(pct: Int) = signaling.send("battery", pct.toString())
    fun syncCurrentBattery() {
        if (role == ConnRole.BABY && hasAcceptedParentSync) batteryPublisher.forceCurrent()
    }
    fun sendCry() = signaling.send("cry", "1")

    fun setRemoteCamera(on: Boolean) = updateDesired(CONTROL_CAMERA, on)
    fun setRemoteMic(on: Boolean) = updateDesired(CONTROL_MICROPHONE, on)
    fun setTorch(on: Boolean) = updateDesired(CONTROL_TORCH, on)
    fun setRemoteCryDetection(on: Boolean) = updateDesired(CONTROL_CRY, on)
    fun setRemoteQuality(saver: Boolean) = updateDesired(CONTROL_VIDEO_SAVER, saver)
    fun sendCryState(on: Boolean) {
        if (role == ConnRole.BABY) {
            actualState = actualState.copy(cryDetection = on)
            sendStateSnapshot()
        }
    }

    fun setParentCameraSharing(on: Boolean) {
        if (role != ConnRole.PARENT) return
        desiredState = desiredState.copy(parentCamera = on)
        val actual = session.setCameraStandby(!on)
        actualState = actualState.copy(parentCamera = actual)
        onControlState(actualState)
        sendControl(CONTROL_PARENT_CAMERA, actual)
    }

    private fun updateDesired(name: String, enabled: Boolean) {
        if (role != ConnRole.PARENT) return
        desiredState = desiredState.withControl(name, enabled)
        sendControl(name, enabled)
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        heartbeatJob?.cancel()
        leaseJob?.cancel()
        syncRetryJob?.cancel()
        torchMaintenanceJob?.cancel()
        torchMaintenanceJob = null
        LullabyPlayer.stop()
        if (role == ConnRole.BABY) setBabyMicrophone(false)
        batteryReceiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        batteryReceiver = null
        session.close()
        if (role == ConnRole.PARENT && currentSyncId != null) {
            signaling.send("parent_disconnect", currentSyncId.orEmpty())
            scope.launch {
                withTimeoutOrNull(DISCONNECT_ACK_TIMEOUT_MS) {
                    disconnectAcknowledged.await()
                }
                signaling.close()
                scope.cancel()
            }
        } else {
            signaling.close()
            scope.cancel()
        }
    }

    override fun onLocalIceCandidate(candidate: IceCandidate) {
        val syncId = currentSyncId ?: return
        val type = Regex("typ (\\w+)").find(candidate.sdp)?.groupValues?.get(1) ?: "?"
        Log.d(TAG, "Local ICE candidate: typ=$type")
        val json = JSONObject()
            .put("syncId", syncId)
            .put("sdpMid", candidate.sdpMid)
            .put("sdpMLineIndex", candidate.sdpMLineIndex)
            .put("candidate", candidate.sdp)
        signaling.send("ice", json.toString())
    }

    override fun onRemoteVideoTrack(track: VideoTrack) = onRemoteVideo(track)
    override fun onRemoteAudioTrack(track: AudioTrack) = onRemoteAudio(track)
    override fun onLocalAudioSamples(samples: ShortArray) {
        if (role == ConnRole.BABY) CryAudioBridge.submitWebRtcSamples(samples)
    }

    override fun onConnectionStateChange(state: PeerConnection.IceConnectionState) {
        if (stopped.get()) return
        onState(state)
        when (state) {
            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.DISCONNECTED,
            -> scheduleReconnect(state)
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED,
            -> reconnectPending.set(false)
            else -> Unit
        }
    }

    private fun scheduleReconnect(state: PeerConnection.IceConnectionState) {
        if (!reconnectPending.compareAndSet(false, true)) return
        scope.launch {
            delay(if (state == PeerConnection.IceConnectionState.FAILED) 1500L else 4000L)
            if (stopped.get()) return@launch
            if (role == ConnRole.BABY) requestBabyOffer(iceRestart = true)
            else beginParentSync()
        }
    }

    private fun receiveIce(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        if (json.optString("syncId") != currentSyncId) return
        runCatching {
            IceCandidate(
                json.getString("sdpMid"),
                json.getInt("sdpMLineIndex"),
                json.getString("candidate"),
            )
        }.getOrNull()?.let(session::addRemoteIceCandidate)
    }

    private fun controlTorch(on: Boolean): Boolean = session.setTorchEnabled(on)

    // WebRTC's camera pipeline periodically re-submits its own repeating CaptureRequest (for
    // autofocus / autoexposure), which overwrites our FLASH_MODE_TORCH setting and silently turns
    // the torch off without any callback. Re-apply every 2 s while torch should be ON.
    private fun maintainTorch(on: Boolean) {
        torchMaintenanceJob?.cancel()
        torchMaintenanceJob = null
        if (!on || role != ConnRole.BABY) return
        torchMaintenanceJob = scope.launch {
            while (isActive && !stopped.get()) {
                delay(2_000)
                if (!stopped.get()) session.setTorchEnabled(true)
            }
        }
    }

    private fun setBabyMicrophone(enabled: Boolean): Boolean {
        if (role != ConnRole.BABY) return session.setLocalAudioEnabled(enabled)
        if (enabled) {
            CryAudioBridge.beforeWebRtcMicEnabled()
            val actual = session.setLocalAudioEnabled(true)
            if (!actual) CryAudioBridge.afterWebRtcMicDisabled()
            return actual
        }
        session.setLocalAudioEnabled(false)
        CryAudioBridge.afterWebRtcMicDisabled()
        return false
    }

    private fun sdpEnvelope(syncId: String, sdp: String): String =
        JSONObject().put("syncId", syncId).put("sdp", sdp).toString()

    private fun parseSdpEnvelope(payload: String): Pair<String, String>? {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val syncId = json.optString("syncId")
        val sdp = json.optString("sdp")
        return if (syncId.isBlank() || sdp.isBlank()) null else syncId to sdp
    }

    private fun stateToJson(state: SessionControlState) = JSONObject()
        .put(CONTROL_CAMERA, state.camera)
        .put(CONTROL_MICROPHONE, state.microphone)
        .put(CONTROL_TORCH, state.torch)
        .put(CONTROL_CRY, state.cryDetection)
        .put(CONTROL_LULLABY, state.lullaby)
        .put(CONTROL_PARENT_CAMERA, state.parentCamera)
        .put(CONTROL_VIDEO_SAVER, state.videoSaver)

    private fun stateFromJson(json: JSONObject) = SessionControlState(
        camera = json.optBoolean(CONTROL_CAMERA),
        microphone = json.optBoolean(CONTROL_MICROPHONE),
        torch = json.optBoolean(CONTROL_TORCH),
        cryDetection = json.optBoolean(CONTROL_CRY),
        lullaby = json.optBoolean(CONTROL_LULLABY),
        parentCamera = json.optBoolean(CONTROL_PARENT_CAMERA),
        videoSaver = json.optBoolean(CONTROL_VIDEO_SAVER),
    )

    companion object {
        private const val TAG = "BabyCam"
        private const val LEASE_HEARTBEAT_MS = 15_000L
        private const val CONTROL_LEASE_MS = 45_000L
        private const val LEASE_CHECK_MS = 5_000L
        private const val SYNC_RETRY_MS = 2_000L
        private const val DISCONNECT_ACK_TIMEOUT_MS = 1_000L
    }
}
