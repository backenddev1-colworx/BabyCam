package com.colworx.babycam.webrtc

import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
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

    /** Public STUN + free Open Relay TURN (no account needed). Same-Wi-Fi works without TURN. */
    private fun iceServers(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
    )

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
                when (val t = receiver?.track()) {
                    is VideoTrack -> listener.onRemoteVideoTrack(t)
                    is AudioTrack -> listener.onRemoteAudioTrack(t)
                    else -> {}
                }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) =
                listener.onConnectionStateChange(state)
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
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
        capturer.startCapture(1280, 720, 30)
        val track = factory.createVideoTrack("VIDEO", source)
        track.setEnabled(true)
        localVideoTrack = track
        peerConnection?.addTrack(track, listOf("STREAM"))
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
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
