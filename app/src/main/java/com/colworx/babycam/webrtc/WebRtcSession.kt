package com.colworx.babycam.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.AudioTrack
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
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

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
        fun onConnectionStateChange(state: PeerConnection.IceConnectionState)
    }

    val eglBase: EglBase = EglBase.create()
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    var localVideoTrack: VideoTrack? = null
        private set
    var localAudioTrack: AudioTrack? = null
        private set

    // Current capture format. Stored so a standby resume and a quality change both apply the
    // same (latest) resolution/fps instead of hard-coding 1280x720x30 in two places.
    private var capWidth = 1280
    private var capHeight = 720
    private var capFps = 30

    /**
     * Google STUN + Metered TURN relay. STUN handles most NATs (direct/reflexive); TURN relays
     * media when direct P2P is impossible (symmetric NAT, client-isolated Wi-Fi, cross-network) —
     * essential for "works on any network". The previous free openrelay.metered.ca is dead (no
     * relay candidates ever allocated), so we use Metered's free tier. The 443/tcp + turns entries
     * survive restrictive firewalls that only allow HTTPS-looking traffic.
     */
    private fun iceServers(): List<PeerConnection.IceServer> {
        val turnUser = "b802535bd8fa917ceca159d0"
        val turnPass = "MaYcJAzJawOa/L5Q"
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
                .setUsername(turnUser).setPassword(turnPass).createIceServer(),
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80?transport=tcp")
                .setUsername(turnUser).setPassword(turnPass).createIceServer(),
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
                .setUsername(turnUser).setPassword(turnPass).createIceServer(),
            PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
                .setUsername(turnUser).setPassword(turnPass).createIceServer(),
        )
    }

    fun initialize() {
        configureAudioRouting()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        val encoder = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()

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
                    is AudioTrack -> listener.onRemoteAudioTrack(t)
                    else -> {}
                }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE connection state -> $state")
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

    /** Baby side: start the front/back camera and add a local video track. */
    fun startCamera(useFront: Boolean = false) {
        val capturer = createCameraCapturer(useFront) ?: return
        videoCapturer = capturer
        val source = factory.createVideoSource(capturer.isScreencast)
        videoSource = source
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        capturer.initialize(surfaceHelper, appContext, source.capturerObserver)
        capturer.startCapture(capWidth, capHeight, capFps)
        val track = factory.createVideoTrack("VIDEO", source)
        track.setEnabled(true)
        localVideoTrack = track
        peerConnection?.addTrack(track, listOf("STREAM"))
    }

    /**
     * Baby side: add a recv-only video transceiver so the OFFER carries a second video m-line the
     * parent can later send its own camera on (on-demand two-way video). Adding it up front means
     * the parent's "Share my camera" never triggers a renegotiation — it just starts a capturer on
     * an already-negotiated m-line. Must be called AFTER [startCamera] so the baby's own camera is
     * video m-line #0 and this receive slot is video m-line #1 (the parent relies on that order).
     */
    fun addParentVideoReceiveSlot() {
        try {
            peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )
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

    /** Add a local microphone audio track (baby always; parent only while talking). */
    fun startAudio() {
        val audioSource = factory.createAudioSource(MediaConstraints())
        val track = factory.createAudioTrack("AUDIO", audioSource)
        track.setEnabled(true)
        localAudioTrack = track
        peerConnection?.addTrack(track, listOf("STREAM"))
    }

    fun setLocalAudioEnabled(enabled: Boolean) { localAudioTrack?.setEnabled(enabled) }
    fun setLocalVideoEnabled(enabled: Boolean) { localVideoTrack?.setEnabled(enabled) }
    fun switchCamera() { videoCapturer?.switchCamera(null) }

    private var cameraStandby = false
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
    fun setCameraStandby(standby: Boolean) {
        if (standby == cameraStandby) return
        cameraStandby = standby
        val capturer = videoCapturer ?: return
        if (standby) {
            try { capturer.stopCapture() } catch (e: Exception) { Log.w(TAG, "stopCapture failed: ${e.message}") }
            localVideoTrack?.setEnabled(false)
        } else {
            try { capturer.startCapture(capWidth, capHeight, capFps) } catch (e: Exception) { Log.w(TAG, "startCapture failed: ${e.message}") }
            localVideoTrack?.setEnabled(true)
        }
    }

    /**
     * Without this, Android routes WebRTC call audio through the earpiece at a much lower
     * max volume — the classic "WebRTC audio is whisper-quiet on Android" gotcha. Force
     * speakerphone + communication mode on both baby and parent so talk/listen is audible.
     */
    private fun configureAudioRouting() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)
    }

    private fun restoreAudioRouting() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun createCameraCapturer(useFront: Boolean): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val names = enumerator.deviceNames
        names.firstOrNull { enumerator.isFrontFacing(it) == useFront }?.let {
            return enumerator.createCapturer(it, null)
        }
        return names.firstOrNull()?.let { enumerator.createCapturer(it, null) }
    }

    fun createOffer(iceRestart: Boolean = false, onCreated: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            if (iceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }
        pc.createOffer(simpleSdpObserver { sdp ->
            pc.setLocalDescription(simpleSdpObserver(), sdp)
            onCreated(sdp)
        }, constraints)
    }

    fun createAnswer(onCreated: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        pc.createAnswer(simpleSdpObserver { sdp ->
            pc.setLocalDescription(simpleSdpObserver(), sdp)
            onCreated(sdp)
        }, MediaConstraints())
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(simpleSdpObserver(), sdp)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun close() {
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        videoCapturer?.dispose()
        videoSource?.dispose()
        surfaceHelper?.dispose()
        peerConnection?.close()
        peerConnection = null
        eglBase.release()
        restoreAudioRouting()
    }

    private fun simpleSdpObserver(onCreate: ((SessionDescription) -> Unit)? = null) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) { onCreate?.invoke(sdp) }
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e(TAG, "SDP create failed: $error") }
        override fun onSetFailure(error: String?) { Log.e(TAG, "SDP set failed: $error") }
    }

    companion object {
        private const val TAG = "BabyCam"
    }
}
