package com.colworx.babycam.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.colworx.babycam.audio.PcmFrameAccumulator
import org.webrtc.AudioTrack
import org.webrtc.AudioSource
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Core WebRTC wrapper for BabyCam. One instance per active connection.
 *
 * The baby unit (offerer) attaches a camera video track + mic audio track and creates an offer.
 * The parent unit (answerer) receives the remote video/audio and may add its own mic track for
 * two-way talk. Signaling (offer/answer/ICE) is transported by the MQTT [com.colworx.babycam.signaling]
 * layer — this class is transport-agnostic and surfaces everything via [Listener].
 *
 * Media is encrypted end-to-end by WebRTC's DTLS-SRTP by default.
 */
class WebRtcSession(
    private val appContext: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onLocalIceCandidate(candidate: IceCandidate)
        fun onRemoteVideoTrack(track: VideoTrack)
        fun onRemoteAudioTrack(track: AudioTrack)
        fun onLocalAudioSamples(samples: ShortArray)
        fun onConnectionStateChange(state: PeerConnection.IceConnectionState)
    }

    val eglBase: EglBase = EglBase.create()
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var audioSource: AudioSource? = null
    private var audioTransceiver: RtpTransceiver? = null
    private var localAudioActive = false
    private var remoteAudioActive = false
    private var remoteAudioTrack: AudioTrack? = null
    private val pcmFrameAccumulator = PcmFrameAccumulator()
    private var savedAudioRouting: AudioRoutingState? = null
    var localVideoTrack: VideoTrack? = null
        private set
    var localAudioTrack: AudioTrack? = null
        private set
    var connectionState: PeerConnection.IceConnectionState? = null
        private set

    // Current capture format. Stored so a standby resume and a quality change both apply the
    // same (latest) resolution/fps instead of hard-coding 1280x720x30 in two places.
    private var capWidth = 1280
    private var capHeight = 720
    private var capFps = 30
    private var activeCameraIsFront = false
    private var lastStatsTimestampUs = 0.0
    private var lastInboundBytes = 0L

    /**
     * Google STUN + Metered TURN relay. STUN handles most NATs (direct/reflexive); TURN relays
     * media when direct P2P is impossible (symmetric NAT, client-isolated Wi-Fi, cross-network) —
     * essential for "works on any network". The previous free openrelay.metered.ca is dead (no
     * relay candidates ever allocated), so we use Metered's free tier. The 443/tcp + turns entries
     * survive restrictive firewalls that only allow HTTPS-looking traffic.
     */
    // TURN/STUN endpoints are defined centrally in TurnConfig so the relay provider can be switched
    // (Metered / Cloudflare / self-hosted coturn / STUN-only) with a one-line change there.
    private var resolvedIceServers: List<PeerConnection.IceServer> = TurnConfig.iceServers()

    // Called from BabyCamConnection.start() (inside a coroutine) when Cloudflare is the active
    // provider so ephemeral credentials can be fetched before the PeerConnection is created.
    // Falls back to Metered if the API call fails (network error / bad token).
    suspend fun resolveCloudflareIceServers() {
        if (TurnConfig.ACTIVE_PROVIDER != TurnConfig.Provider.CLOUDFLARE) return
        val servers = CloudflareTurnFetcher.fetch()
        resolvedIceServers = if (servers != null) {
            TurnConfig.iceServers() + servers
        } else {
            Log.w(TAG, "Cloudflare TURN fetch failed — falling back to Metered")
            TurnConfig.Metered.servers().let { TurnConfig.iceServers() + it }
        }
    }

    private fun iceServers(): List<PeerConnection.IceServer> = resolvedIceServers

    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        val encoder = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setSamplesReadyCallback { audioSamples ->
                if (audioSamples.audioFormat != android.media.AudioFormat.ENCODING_PCM_16BIT) {
                    return@setSamplesReadyCallback
                }
                val bytes = audioSamples.data
                val shorts = ShortArray(bytes.size / 2)
                ByteBuffer.wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                    .get(shorts)
                val targetSize = (audioSamples.sampleRate / 10) * audioSamples.channelCount
                pcmFrameAccumulator.append(shorts, targetSize).forEach(listener::onLocalAudioSamples)
            }
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
        audioDeviceModule.release()

        val config = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peerConnection = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) = listener.onLocalIceCandidate(candidate)
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out MediaStream>?) {
                val t = receiver?.track()
                Log.d(TAG, "onAddTrack: kind=${t?.kind()} id=${t?.id()}")
                when (t) {
                    is VideoTrack -> listener.onRemoteVideoTrack(t)
                    is AudioTrack -> {
                        remoteAudioTrack = t
                        t.setEnabled(remoteAudioActive)
                        listener.onRemoteAudioTrack(t)
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE connection state -> $state")
                connectionState = state
                listener.onConnectionStateChange(state)
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state -> $p0")
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state -> $state")
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })
    }

    /**
     * Provisions the camera track without opening the camera. Capture starts only after an
     * explicit [setCameraStandby] call with `false`.
     */
    fun startCamera(useFront: Boolean = false) {
        if (localVideoTrack != null) return
        val capturer = createCameraCapturer(useFront) ?: return
        videoCapturer = capturer
        val source = factory.createVideoSource(capturer.isScreencast)
        videoSource = source
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        capturer.initialize(surfaceHelper, appContext, source.capturerObserver)
        val track = factory.createVideoTrack("VIDEO", source)
        track.setEnabled(false)
        localVideoTrack = track
        peerConnection?.addTrack(track, listOf("STREAM"))
        cameraStandby = true
    }

    /**
     * Baby side: add a recv-only video transceiver so the OFFER carries a second video m-line the
     * parent can later send its own camera on (on-demand two-way video). Adding it up front means
     * the parent's "Share my camera" never triggers a renegotiation — it just starts a capturer on
     * an already-negotiated m-line. Must be called AFTER [startCamera] so the baby's own camera is
     * video m-line #0 and this receive slot is video m-line #1 (the parent relies on that order).
     */
    fun addParentVideoReceiveSlot() {
        if (parentVideoReceiveSlotAdded) return
        try {
            peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )
            parentVideoReceiveSlotAdded = true
        } catch (e: Exception) {
            // If this fails the core baby->parent stream is unaffected; two-way video just won't
            // be available. Never let it break the primary monitor function.
            Log.w(TAG, "addParentVideoReceiveSlot failed: ${e.message}")
        }
    }

    /**
     * Parent side: attach the parent's (front) camera to the baby's recv-only video slot so the
     * parent can stream video back on demand. Reuses [videoCapturer]/[videoSource]/[localVideoTrack]
     * (unused on the parent otherwise) so [setCameraStandby] then controls the parent's own camera.
     * The capturer starts in STANDBY (stopped, track disabled) — no frames flow until the parent
     * actually taps "Share my camera". Must be called AFTER [setRemoteDescription] of the baby's
     * offer, so the matching transceiver (video m-line #1) already exists.
     */
    fun attachParentCamera(useFront: Boolean = true) {
        val pc = peerConnection ?: return
        // Idempotent: the parent receives multiple offers (initial, re-offer on ping, ICE restart).
        // Only attach the camera once — a second attach would leak a capturer and duplicate tracks.
        if (localVideoTrack != null) return
        try {
            // The 2nd video transceiver corresponds to the baby's recv-only slot (deterministic by
            // m-line order — the 1st video transceiver is the baby's camera we receive).
            val slot = pc.transceivers
                .filter { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO }
                .getOrNull(1) ?: run {
                    Log.w(TAG, "attachParentCamera: no parent video slot found")
                    return
                }
            val capturer = createCameraCapturer(useFront) ?: return
            videoCapturer = capturer
            val source = factory.createVideoSource(capturer.isScreencast)
            videoSource = source
            surfaceHelper = SurfaceTextureHelper.create("ParentCapture", eglBase.eglBaseContext)
            capturer.initialize(surfaceHelper, appContext, source.capturerObserver)
            val track = factory.createVideoTrack("PARENT_VIDEO", source)
            track.setEnabled(false)
            localVideoTrack = track
            slot.sender.setTrack(track, false)
            slot.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
            // Start in standby: capturer NOT started yet, so zero battery/bandwidth until shared.
            cameraStandby = true
        } catch (e: Exception) {
            Log.w(TAG, "attachParentCamera failed: ${e.message}")
        }
    }

    /**
     * Changes the live capture resolution/fps (battery-saver lever). Safe to call while streaming —
     * no renegotiation. If the camera is currently in standby the new format is just stored and
     * applied on the next resume.
     */
    fun changeCaptureFormat(width: Int, height: Int, fps: Int) {
        capWidth = width
        capHeight = height
        capFps = fps
        if (!cameraStandby) {
            try {
                videoCapturer?.changeCaptureFormat(width, height, fps)
            } catch (e: Exception) {
                Log.w(TAG, "changeCaptureFormat failed: ${e.message}")
            }
        }
    }

    /**
     * Negotiates an audio m-line without opening microphone hardware. The local source is attached
     * only by [setLocalAudioEnabled], so default-OFF is a real capture-off state.
     */
    fun startAudio() {
        if (audioTransceiver != null) return
        audioTransceiver = peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV),
        )
    }

    fun setLocalAudioEnabled(enabled: Boolean): Boolean {
        return safeMediaMutation {
            val transceiver = peerConnection?.transceivers
                ?.firstOrNull { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO }
                ?: audioTransceiver
                ?: return@safeMediaMutation false
            audioTransceiver = transceiver
            val hasAttachedTrack = localAudioTrack != null
            if (!shouldMutateAudioSender(enabled, hasAttachedTrack)) {
                return@safeMediaMutation localAudioTrack?.enabled() == true
            }
            if (!enabled) {
                // Use runCatching so an IllegalStateException from a closing PeerConnection
                // never prevents the local track and source from being cleaned up — if setTrack
                // threw and we skipped the dispose/null lines, the sender would keep streaming.
                runCatching { transceiver.sender.setTrack(null, false) }
                localAudioTrack?.dispose()
                localAudioTrack = null
                audioSource?.dispose()
                audioSource = null
                localAudioActive = false
                updateAudioRouting()
                return@safeMediaMutation false
            }
            val source = factory.createAudioSource(MediaConstraints())
            audioSource = source
            val track = factory.createAudioTrack("AUDIO", source)
            track.setEnabled(true)
            if (!transceiver.sender.setTrack(track, false)) {
                track.dispose()
                source.dispose()
                audioSource = null
                return@safeMediaMutation false
            }
            localAudioTrack = track
            localAudioActive = true
            updateAudioRouting()
            true
        }
    }

    // Called at the start of each parent-sync cycle to guarantee the audio sender has no track
    // before the new offer is created, regardless of how the previous session ended. Without this,
    // a failed fail-safe cleanup can leave the disposed track on the sender and audio leaks on
    // reconnect even though both sides show mic OFF.
    fun forceResetAudioSender() {
        val transceiver = peerConnection?.transceivers
            ?.firstOrNull { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO }
            ?: audioTransceiver ?: return
        audioTransceiver = transceiver
        runCatching { transceiver.sender.setTrack(null, false) }
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        localAudioActive = false
        updateAudioRouting()
    }

    fun setRemoteAudioPlaybackActive(active: Boolean) {
        remoteAudioActive = active
        remoteAudioTrack?.setEnabled(active)
        updateAudioRouting()
    }

    suspend fun loadParentDiagnostics(
        signalingUp: Boolean,
        connectionState: String,
        qualityMode: String,
    ): ParentStreamDiagnostics = suspendCancellableCoroutine { continuation ->
        val pc = peerConnection
        if (pc == null) {
            continuation.resume(
                ParentStreamDiagnostics(
                    signalingLabel = if (signalingUp) "Up" else "Down",
                    connectionLabel = connectionState,
                    qualityModeLabel = qualityMode,
                )
            )
            return@suspendCancellableCoroutine
        }
        pc.getStats { report ->
            continuation.resume(parseDiagnosticsReport(report, signalingUp, connectionState, qualityMode))
        }
    }

    fun setLocalVideoEnabled(enabled: Boolean): Boolean {
        val track = localVideoTrack ?: return false
        track.setEnabled(enabled)
        return track.enabled()
    }
    fun switchCamera() {
        setTorchEnabled(false)
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                activeCameraIsFront = isFrontCamera
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.w(TAG, "Camera switch failed: $errorDescription")
            }
        })
    }

    fun setTorchEnabled(enabled: Boolean): Boolean {
        val capturer = videoCapturer
        if (!enabled) {
            Camera2TorchController.setTorch(capturer, false)
            return false
        }
        if (!canEnableTorch(
                isFrontCamera = activeCameraIsFront,
                cameraStandby = cameraStandby,
                sessionAvailable = Camera2TorchController.hasActiveSession(capturer),
            )
        ) {
            return false
        }
        return Camera2TorchController.setTorch(capturer, true)
    }

    private var cameraStandby = false
    private var parentVideoReceiveSlotAdded = false
    private var offerInProgress = false
    val isCameraInStandby: Boolean get() = cameraStandby

    /**
     * Real power-save for the baby's camera, used when the parent turns the baby's camera off.
     * Just disabling the video track (the old behavior) silences outgoing video but leaves the
     * Camera2 capturer running — the ISP keeps producing frames, the encoder keeps being fed, and
     * the phone keeps drawing power and heating up exactly as if it were still streaming. Calling
     * [CameraVideoCapturer.stopCapture] actually halts the camera pipeline, which is where the
     * real battery/heat cost is. [startCapture] resumes it when the parent turns the camera back
     * on. The track itself is left in place (no renegotiation needed) — only enabled/disabled.
     */
    fun setCameraStandby(standby: Boolean): Boolean {
        if (standby == cameraStandby) return !standby && localVideoTrack?.enabled() == true
        val capturer = videoCapturer ?: return false
        if (standby) {
            try { capturer.stopCapture() } catch (e: Exception) { Log.w(TAG, "stopCapture failed: ${e.message}") }
            localVideoTrack?.setEnabled(false)
            cameraStandby = true
        } else {
            val started = try {
                capturer.startCapture(capWidth, capHeight, capFps)
                true
            } catch (e: Exception) {
                Log.w(TAG, "startCapture failed: ${e.message}")
                false
            }
            if (!started) {
                localVideoTrack?.setEnabled(false)
                cameraStandby = true
                return false
            }
            localVideoTrack?.setEnabled(true)
            cameraStandby = false
        }
        return !cameraStandby && localVideoTrack?.enabled() == true
    }

    /**
     * Without this, Android routes WebRTC call audio through the earpiece at a much lower
     * max volume — the classic "WebRTC audio is whisper-quiet on Android" gotcha. Force
     * speakerphone + communication mode on both baby and parent so talk/listen is audible.
     */
    private fun updateAudioRouting() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (localAudioActive || remoteAudioActive) {
            if (savedAudioRouting == null) {
                savedAudioRouting = AudioRoutingState(
                    mode = audioManager.mode,
                    speakerphoneOn = audioManager.isSpeakerphoneOn,
                    voiceCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL),
                )
            }
            val target = activeAudioRoutingTarget(
                original = savedAudioRouting ?: return,
                communicationMode = AudioManager.MODE_IN_COMMUNICATION,
                maxVoiceCallVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
            )
            audioManager.mode = target.mode
            audioManager.isSpeakerphoneOn = target.speakerphoneOn
            if (audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) != target.voiceCallVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, target.voiceCallVolume, 0)
            }
        } else {
            restoreAudioRouting()
        }
    }

    private fun restoreAudioRouting() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val original = savedAudioRouting
        if (original != null) {
            val target = restoreAudioRoutingTarget(original)
            if (audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) != target.voiceCallVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, target.voiceCallVolume, 0)
            }
            audioManager.isSpeakerphoneOn = target.speakerphoneOn
            audioManager.mode = target.mode
            savedAudioRouting = null
            return
        }
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun createCameraCapturer(useFront: Boolean): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val names = enumerator.deviceNames
        names.firstOrNull { enumerator.isFrontFacing(it) == useFront }?.let {
            activeCameraIsFront = enumerator.isFrontFacing(it)
            return enumerator.createCapturer(it, null)
        }
        return names.firstOrNull()?.let {
            activeCameraIsFront = enumerator.isFrontFacing(it)
            enumerator.createCapturer(it, null)
        }
    }

    @Synchronized
    fun createOffer(
        iceRestart: Boolean = false,
        onCreated: (SessionDescription) -> Unit,
        onFailure: () -> Unit = {},
    ): Boolean {
        val pc = peerConnection ?: return false
        if (offerInProgress) return false
        offerInProgress = true
        val constraints = MediaConstraints().apply {
            if (iceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        finishOffer()
                        onCreated(sdp)
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Set local offer failed: $error")
                        finishOffer()
                        onFailure()
                    }

                    override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                    override fun onCreateFailure(error: String?) = Unit
                }, sdp)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create offer failed: $error")
                finishOffer()
                onFailure()
            }

            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }, constraints)
        return true
    }

    fun createAnswer(onCreated: (SessionDescription) -> Unit, onFailure: () -> Unit = {}) {
        val pc = peerConnection ?: return
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() = onCreated(sdp)
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Set local answer failed: $error")
                        onFailure()
                    }

                    override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                    override fun onCreateFailure(error: String?) = Unit
                }, sdp)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create answer failed: $error")
                onFailure()
            }

            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }, MediaConstraints())
    }

    fun setRemoteDescription(
        sdp: SessionDescription,
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        val pc = peerConnection ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() = onSuccess()
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set remote SDP failed: $error")
                onFailure()
            }

            override fun onCreateSuccess(sdp: SessionDescription?) = Unit
            override fun onCreateFailure(error: String?) = Unit
        }, sdp)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun close() {
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        audioTransceiver?.sender?.setTrack(null, false)
        localAudioActive = false
        remoteAudioActive = false
        remoteAudioTrack = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        videoCapturer?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        audioSource = null
        surfaceHelper?.dispose()
        peerConnection?.close()
        peerConnection = null
        eglBase.release()
        restoreAudioRouting()
    }

    private fun parseDiagnosticsReport(
        report: RTCStatsReport,
        signalingUp: Boolean,
        connectionState: String,
        qualityMode: String,
    ): ParentStreamDiagnostics {
        val stats = report.statsMap.values
        val inboundVideo = stats.firstOrNull { it.type == "inbound-rtp" && it.isVideoInbound() }
        val selectedPair = stats.firstOrNull { it.type == "candidate-pair" && it.isSelectedCandidatePair() }
        val localCandidate = selectedPair?.linkedCandidate(stats, "localCandidateId")
        val remoteCandidate = selectedPair?.linkedCandidate(stats, "remoteCandidateId")

        val width = inboundVideo?.longMember("frameWidth")
        val height = inboundVideo?.longMember("frameHeight")
        val fps = inboundVideo?.doubleMember("framesPerSecond")
        val bytes = inboundVideo?.longMember("bytesReceived")
        val packetsReceived = inboundVideo?.longMember("packetsReceived")
        val packetsLost = inboundVideo?.longMember("packetsLost")
        val bitrateKbps = calculateInboundBitrateKbps(bytes, inboundVideo?.timestampUs)
        val packetLossPercent = if (packetsReceived != null && packetsLost != null && packetsReceived + packetsLost > 0) {
            (packetsLost.toDouble() * 100.0) / (packetsReceived + packetsLost).toDouble()
        } else {
            null
        }

        return ParentStreamDiagnostics(
            resolutionLabel = if (width != null && height != null) "${width}x$height" else "--",
            fpsLabel = fps?.let { "${it.toInt()} fps" } ?: "--",
            bitrateLabel = bitrateKbps?.let { "${it.toInt()} kbps" } ?: "--",
            packetLossLabel = packetLossPercent?.let { String.format("%.1f%%", it) } ?: "--",
            icePathLabel = diagnosticsIcePath(
                localCandidateType = localCandidate?.stringMember("candidateType"),
                remoteCandidateType = remoteCandidate?.stringMember("candidateType"),
            ),
            signalingLabel = if (signalingUp) "Up" else "Down",
            connectionLabel = connectionState,
            qualityModeLabel = qualityMode,
        )
    }

    private fun calculateInboundBitrateKbps(bytesReceived: Long?, timestampUs: Double?): Double? {
        if (bytesReceived == null || timestampUs == null) return null
        if (lastStatsTimestampUs == 0.0 || timestampUs <= lastStatsTimestampUs || bytesReceived < lastInboundBytes) {
            lastStatsTimestampUs = timestampUs
            lastInboundBytes = bytesReceived
            return null
        }
        val deltaBytes = bytesReceived - lastInboundBytes
        val deltaSeconds = (timestampUs - lastStatsTimestampUs) / 1_000_000.0
        lastStatsTimestampUs = timestampUs
        lastInboundBytes = bytesReceived
        if (deltaSeconds <= 0.0) return null
        return (deltaBytes * 8.0) / deltaSeconds / 1000.0
    }

    private fun RTCStats.isVideoInbound(): Boolean {
        val kind = members["kind"] as? String
        val mediaType = members["mediaType"] as? String
        return kind == "video" || mediaType == "video"
    }

    private fun RTCStats.isSelectedCandidatePair(): Boolean =
        (members["selected"] as? Boolean) == true || (members["state"] as? String) == "succeeded"

    private fun RTCStats.linkedCandidate(stats: Collection<RTCStats>, memberName: String): RTCStats? {
        val candidateId = members[memberName] as? String ?: return null
        return stats.firstOrNull { it.id == candidateId }
    }

    private fun RTCStats.longMember(name: String): Long? = when (val value = members[name]) {
        is Number -> value.toLong()
        else -> null
    }

    private fun RTCStats.doubleMember(name: String): Double? = when (val value = members[name]) {
        is Number -> value.toDouble()
        else -> null
    }

    private fun RTCStats.stringMember(name: String): String? = members[name] as? String

    @Synchronized
    private fun finishOffer() {
        offerInProgress = false
    }

    companion object {
        private const val TAG = "BabyCam"
    }
}
