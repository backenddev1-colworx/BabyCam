package com.colworx.babycam.webrtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.util.Log
import com.colworx.babycam.audio.LullabyPlayer
import com.colworx.babycam.data.AppPreferences
import com.colworx.babycam.service.CryNotifier
import com.colworx.babycam.signaling.SignalMessage
import com.colworx.babycam.signaling.SignalingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnRole { BABY, PARENT }

/**
 * Orchestrates a full baby<->parent WebRTC session: ties [WebRtcSession] to the MQTT
 * [SignalingClient]. Baby is the offerer (camera+mic); parent answers and receives video.
 */
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
) : WebRtcSession.Listener {

    private val selfId = UUID.randomUUID().toString().take(8)
    private val session = WebRtcSession(context, this)
    private val signaling = SignalingClient(selfId)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var batteryReceiver: BroadcastReceiver? = null
    private val batteryPublisher = BatteryPublisher(::sendBattery)
    private val iceRestarting = AtomicBoolean(false)

    val eglContext get() = session.eglBase.eglBaseContext
    val localVideoTrack get() = session.localVideoTrack

    fun start() {
        scope.launch {
            session.initialize()
            if (role == ConnRole.BABY) {
                session.startCamera(useFront = false)
                val track = session.localVideoTrack
                if (track != null) {
                    onLocalVideo(track)
                } else {
                    Log.e(TAG, "Baby: camera capturer unavailable — no local video track created")
                }
                // Reserve a recv-only video m-line (#1) so the parent can stream its camera back
                // on demand without a later renegotiation. Must come after startCamera (#0).
                session.addParentVideoReceiveSlot()
                session.startAudio()
            }
            signaling.connect(
                room = room,
                onMessage = ::onSignal,
                onReconnected = {
                    Log.d(TAG, "MQTT reconnected, re-initiating for role=$role")
                    scope.launch {
                        when (role) {
                            ConnRole.BABY -> session.createOffer { sdp ->
                                Log.d(TAG, "Baby: re-offer after MQTT reconnect")
                                signaling.send("offer", sdp.description, retained = true)
                                syncCurrentBattery()
                            }
                            ConnRole.PARENT -> {
                                Log.d(TAG, "Parent: re-ping after MQTT reconnect")
                                signaling.send("ping", "")
                            }
                        }
                    }
                }
            ) { up ->
                onSignalingUp(up)
                Log.d(TAG, "Signaling ${if (up) "UP" else "DOWN"}, role=$role")
                if (up) {
                    when (role) {
                        ConnRole.BABY -> {
                            session.createOffer { sdp ->
                                Log.d(TAG, "Baby: initial offer created")
                                signaling.send("offer", sdp.description, retained = true)
                            }
                            registerBatteryReceiver()
                        }
                        ConnRole.PARENT -> {
                            // Pre-add a muted audio track so it's already in the SDP answer.
                            // This avoids renegotiation when user presses Talk later.
                            session.startAudio()
                            session.setLocalAudioEnabled(false)
                            signaling.send("ping", "")
                            Log.d(TAG, "Parent: sent ping, audio pre-added (muted)")
                        }
                    }
                }
            }
        }
    }

    /** Baby side: report battery changes and remember the current level for forced syncs. */
    private fun registerBatteryReceiver() {
        if (role != ConnRole.BABY || batteryReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                observeBattery(intent)
            }
        }
        batteryReceiver = receiver
        val sticky = context.applicationContext
            .registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let(::observeBattery)
    }

    private fun observeBattery(intent: Intent) {
        batteryPublisher.observe(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        )
    }

    private fun onSignal(msg: SignalMessage) {
        when (msg.type) {
            "ping" -> if (role == ConnRole.BABY) {
                Log.d(TAG, "Baby: got ping, re-offering with fresh ICE")
                session.createOffer { sdp ->
                    Log.d(TAG, "Baby: fresh offer created for parent")
                    signaling.send("offer", sdp.description, retained = true)
                }
                syncCurrentBattery()
                // Tell the (re)connecting parent the current cry-detection state so its toggle is
                // accurate without the parent having to change anything.
                scope.launch { sendCryState(AppPreferences(context).cryDetectionEnabled.first()) }
            }
            "remote_cam" -> if (role == ConnRole.BABY) {
                val on = msg.payload == "on"
                // Real power-save: stops the Camera2 capturer entirely when off (not just the
                // track), so the phone actually stops drawing power/heating up — see
                // WebRtcSession.setCameraStandby for why a track-only disable wasn't enough.
                session.setCameraStandby(standby = !on)
                LiveSession.babyCamEnabled.value = on
                Log.d(TAG, "Baby: camera ${if (on) "ON" else "OFF (power-save)"} by parent")
            }
            "remote_mic" -> if (role == ConnRole.BABY) {
                val on = msg.payload == "on"
                session.setLocalAudioEnabled(on)
                Log.d(TAG, "Baby: mic ${if (on) "ON" else "OFF"} by parent")
            }
            "torch" -> if (role == ConnRole.BABY) {
                val on = msg.payload == "on"
                controlTorch(on)
            }
            "switch_camera" -> if (role == ConnRole.BABY) {
                Log.d(TAG, "Baby: switching camera by parent request")
                session.switchCamera()
            }
            "offer" -> if (role == ConnRole.PARENT) {
                Log.d(TAG, "Parent: received offer, creating answer")
                session.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, msg.payload))
                // Attach the parent's own camera to the baby's recv-only slot (kept in standby) so
                // "Share my camera" later is a zero-renegotiation start. Idempotent across re-offers.
                session.attachParentCamera(useFront = true)
                session.createAnswer { sdp ->
                    Log.d(TAG, "Parent: answer created")
                    signaling.send("answer", sdp.description)
                }
            }
            "answer" -> if (role == ConnRole.BABY) {
                Log.d(TAG, "Baby: received answer")
                session.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, msg.payload))
            }
            "ice" -> parseIce(msg.payload)?.let { session.addRemoteIceCandidate(it) }
            "lullaby" -> if (role == ConnRole.BABY) {
                // The parent UI now sends only "bell" (ring) / "stop". The loud alarm-stream
                // bell needs the app context to max out the alarm volume.
                if (msg.payload == "stop") LullabyPlayer.stop()
                else LullabyPlayer.play(LullabyPlayer.Sound.BELL, context.applicationContext)
            }
            "video_enabled" -> if (role == ConnRole.BABY) {
                session.localVideoTrack?.setEnabled(msg.payload == "true")
            }
            "battery" -> if (role == ConnRole.PARENT) {
                msg.payload.toIntOrNull()?.let { onBatteryUpdate(it) }
            }
            "cry" -> if (role == ConnRole.PARENT) {
                // Only post the lock-screen alert if the parent has opted into notifications.
                scope.launch {
                    if (AppPreferences(context).notificationsEnabled.first()) {
                        CryNotifier.postCryAlert(context)
                    }
                }
                onCryAlert()
            }
            "torch_state" -> if (role == ConnRole.PARENT) {
                onTorchState(msg.payload == "on")
            }
            "remote_cry" -> if (role == ConnRole.BABY) {
                val on = msg.payload == "on"
                // Persist so MonitorService starts/stops the detector; echo back so the parent UI
                // reflects the real state.
                scope.launch { AppPreferences(context).setCryDetectionEnabled(on) }
                sendCryState(on)
                Log.d(TAG, "Baby: cry detection ${if (on) "ON" else "OFF"} by parent")
            }
            "cry_state" -> if (role == ConnRole.PARENT) {
                LiveSession.babyCryDetectionEnabled.value = msg.payload == "on"
            }
            "remote_quality" -> if (role == ConnRole.BABY) {
                if (msg.payload == "saver") session.changeCaptureFormat(640, 480, 15)
                else session.changeCaptureFormat(1280, 720, 30)
                Log.d(TAG, "Baby: capture quality -> ${msg.payload}")
            }
            "parent_cam" -> if (role == ConnRole.BABY) {
                // The parent started/stopped sharing its camera; drives the PiP on the baby UI.
                LiveSession.parentCamSharing.value = msg.payload == "on"
                Log.d(TAG, "Baby: parent camera sharing ${msg.payload}")
            }
            else -> {}
        }
    }

    /** Parent: enable/disable own mic for two-way talk. Audio track is pre-added in start(). */
    fun setTalking(on: Boolean) {
        if (role == ConnRole.PARENT) session.setLocalAudioEnabled(on)
    }

    /** Local flip (only meaningful on the device that actually owns a camera, i.e. the baby). */
    fun switchCamera() = session.switchCamera()

    /** Parent: ask the baby phone to flip its own front/back camera. */
    fun requestRemoteCameraSwitch() = signaling.send("switch_camera", "")

    fun sendLullaby(sound: String) = signaling.send("lullaby", sound)

    fun setVideoEnabled(enabled: Boolean) = signaling.send("video_enabled", enabled.toString())

    /** Baby: publish current battery percentage to the parent. */
    fun sendBattery(pct: Int) = signaling.send("battery", pct.toString())

    /** Baby: republish the last known battery percentage for a reconnecting parent. */
    fun syncCurrentBattery() {
        if (role == ConnRole.BABY) batteryPublisher.forceCurrent()
    }

    /** Baby: alert the parent that the baby is crying. */
    fun sendCry() = signaling.send("cry", "1")

    fun stop() {
        LullabyPlayer.stop()
        batteryReceiver?.let { rcv ->
            runCatching { context.applicationContext.unregisterReceiver(rcv) }
        }
        batteryReceiver = null
        signaling.close()
        session.close()
        scope.cancel()
    }

    // WebRtcSession.Listener
    override fun onLocalIceCandidate(candidate: IceCandidate) {
        // Log the candidate type (host / srflx = STUN-reflexive / relay = TURN) so we can tell
        // whether STUN and TURN are actually working — "relay" means the TURN server allocated.
        val type = Regex("typ (\\w+)").find(candidate.sdp)?.groupValues?.get(1) ?: "?"
        Log.d(TAG, "Local ICE candidate: typ=$type")
        val json = JSONObject()
            .put("sdpMid", candidate.sdpMid)
            .put("sdpMLineIndex", candidate.sdpMLineIndex)
            .put("candidate", candidate.sdp)
        signaling.send("ice", json.toString())
    }

    override fun onRemoteVideoTrack(track: VideoTrack) = onRemoteVideo(track)
    override fun onRemoteAudioTrack(track: AudioTrack) = onRemoteAudio(track)

    override fun onConnectionStateChange(state: PeerConnection.IceConnectionState) {
        onState(state)
        when (state) {
            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                if (role == ConnRole.BABY) {
                    val delay = if (state == PeerConnection.IceConnectionState.FAILED) 1500L else 4000L
                    scheduleIceRestart(delayMs = delay)
                } else {
                    scheduleParentReconnect()
                }
            }
            else -> {}
        }
    }

    /** Parent side: ask baby to re-offer, which triggers a fresh ICE negotiation. */
    private fun scheduleParentReconnect() {
        if (!iceRestarting.compareAndSet(false, true)) return
        scope.launch {
            try {
                delay(3000L)
                Log.d(TAG, "Parent: requesting re-offer from baby after ICE failure")
                signaling.send("ping", "")
            } finally {
                iceRestarting.set(false)
            }
        }
    }

    /**
     * Baby side: recover a broken connection by renegotiating with `IceRestart`.
     * Debounced via [iceRestarting] so transient flaps don't spam new offers.
     */
    private fun scheduleIceRestart(delayMs: Long) {
        if (!iceRestarting.compareAndSet(false, true)) return
        scope.launch {
            try {
                delay(delayMs)
                session.createOffer(iceRestart = true) { sdp ->
                    signaling.send("offer", sdp.description)
                }
            } finally {
                iceRestarting.set(false)
            }
        }
    }

    /**
     * Toggles the flash unit while the camera is already open for WebRTC capture. Some OEM
     * camera HALs refuse torch changes while a capture session is active (CAMERA_IN_USE) — in
     * that case we report the real (failed) state back to the parent so the UI doesn't show
     * "Torch ON" when nothing actually turned on.
     */
    private fun controlTorch(on: Boolean) {
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                Log.e(TAG, "Torch control failed: no camera with a flash unit")
                signaling.send("torch_state", "off")
                return
            }
            manager.setTorchMode(cameraId, on)
            Log.d(TAG, "Torch ${if (on) "ON" else "OFF"}")
            signaling.send("torch_state", if (on) "on" else "off")
        } catch (e: Exception) {
            Log.e(TAG, "Torch control failed (camera likely in use for streaming): ${e.message}")
            signaling.send("torch_state", "off")
        }
    }

    fun setRemoteCamera(on: Boolean) = signaling.send("remote_cam", if (on) "on" else "off")
    fun setRemoteMic(on: Boolean) = signaling.send("remote_mic", if (on) "on" else "off")
    fun setTorch(on: Boolean) = signaling.send("torch", if (on) "on" else "off")

    /** Parent: turn the baby's cry detection on/off remotely. */
    fun setRemoteCryDetection(on: Boolean) = signaling.send("remote_cry", if (on) "on" else "off")

    /** Parent: set the baby's capture quality (true = battery-saver low-res). */
    fun setRemoteQuality(saver: Boolean) = signaling.send("remote_quality", if (saver) "saver" else "high")

    /** Baby: report its current cry-detection state to the parent (keeps the parent toggle synced). */
    fun sendCryState(on: Boolean) = signaling.send("cry_state", if (on) "on" else "off")

    /**
     * Parent: start/stop streaming the parent's own camera back to the baby (on-demand two-way).
     * The track was pre-attached in standby by [attachParentCamera], so this just starts/stops the
     * capturer — no renegotiation — and tells the baby whether to show the picture-in-picture.
     */
    fun setParentCameraSharing(on: Boolean) {
        if (role != ConnRole.PARENT) return
        session.setCameraStandby(standby = !on)
        signaling.send("parent_cam", if (on) "on" else "off")
    }

    private fun parseIce(payload: String): IceCandidate? = try {
        val o = JSONObject(payload)
        IceCandidate(o.getString("sdpMid"), o.getInt("sdpMLineIndex"), o.getString("candidate"))
    } catch (_: Exception) { null }

    companion object {
        private const val TAG = "BabyCam"
    }
}
