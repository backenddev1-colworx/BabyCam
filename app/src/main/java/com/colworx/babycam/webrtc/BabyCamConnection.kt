package com.colworx.babycam.webrtc

import android.content.Context
import com.colworx.babycam.audio.LullabyPlayer
import com.colworx.babycam.signaling.SignalMessage
import com.colworx.babycam.signaling.SignalingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

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
    private val onRemoteAudio: (AudioTrack) -> Unit = {},
    private val onState: (PeerConnection.IceConnectionState) -> Unit = {},
    private val onSignalingUp: (Boolean) -> Unit = {},
) : WebRtcSession.Listener {

    private val selfId = UUID.randomUUID().toString().take(8)
    private val session = WebRtcSession(context, this)
    private val signaling = SignalingClient(selfId)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val eglContext get() = session.eglBase.eglBaseContext
    val localVideoTrack get() = session.localVideoTrack

    fun start() {
        scope.launch {
            session.initialize()
            if (role == ConnRole.BABY) {
                session.startCamera(useFront = false)
                session.startAudio()
            }
            signaling.connect(room, ::onSignal) { up ->
                onSignalingUp(up)
                if (up && role == ConnRole.BABY) {
                    session.createOffer { sdp -> signaling.send("offer", sdp.description) }
                }
            }
        }
    }

    private fun onSignal(msg: SignalMessage) {
        when (msg.type) {
            "offer" -> if (role == ConnRole.PARENT) {
                session.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, msg.payload))
                session.createAnswer { sdp -> signaling.send("answer", sdp.description) }
            }
            "answer" -> if (role == ConnRole.BABY) {
                session.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, msg.payload))
            }
            "ice" -> parseIce(msg.payload)?.let { session.addRemoteIceCandidate(it) }
            "lullaby" -> if (role == ConnRole.BABY) {
                if (msg.payload == "stop") LullabyPlayer.stop()
                else LullabyPlayer.play(
                    when (msg.payload) {
                        "heartbeat" -> LullabyPlayer.Sound.HEARTBEAT
                        "rain" -> LullabyPlayer.Sound.RAIN
                        else -> LullabyPlayer.Sound.WHITE_NOISE
                    }
                )
            }
            "video_enabled" -> if (role == ConnRole.BABY) {
                session.localVideoTrack?.setEnabled(msg.payload == "true")
            }
            else -> {}
        }
    }

    /** Parent: enable/disable own mic for two-way talk. */
    fun setTalking(on: Boolean) {
        if (role == ConnRole.PARENT) {
            if (on && session.localAudioTrack == null) session.startAudio()
            session.setLocalAudioEnabled(on)
        }
    }

    fun switchCamera() = session.switchCamera()

    fun sendLullaby(sound: String) = signaling.send("lullaby", sound)

    fun setVideoEnabled(enabled: Boolean) = signaling.send("video_enabled", enabled.toString())

    fun stop() {
        LullabyPlayer.stop()
        signaling.close()
        session.close()
        scope.cancel()
    }

    // WebRtcSession.Listener
    override fun onLocalIceCandidate(candidate: IceCandidate) {
        val json = JSONObject()
            .put("sdpMid", candidate.sdpMid)
            .put("sdpMLineIndex", candidate.sdpMLineIndex)
            .put("candidate", candidate.sdp)
        signaling.send("ice", json.toString())
    }

    override fun onRemoteVideoTrack(track: VideoTrack) = onRemoteVideo(track)
    override fun onRemoteAudioTrack(track: AudioTrack) = onRemoteAudio(track)
    override fun onConnectionStateChange(state: PeerConnection.IceConnectionState) = onState(state)

    private fun parseIce(payload: String): IceCandidate? = try {
        val o = JSONObject(payload)
        IceCandidate(o.getString("sdpMid"), o.getInt("sdpMLineIndex"), o.getString("candidate"))
    } catch (_: Exception) { null }
}
