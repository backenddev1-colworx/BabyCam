package com.colworx.babycam.webrtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.colworx.babycam.audio.LullabyPlayer
import com.colworx.babycam.service.CryNotifier
import com.colworx.babycam.signaling.SignalMessage
import com.colworx.babycam.signaling.SignalingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private val onRemoteAudio: (AudioTrack) -> Unit = {},
    private val onState: (PeerConnection.IceConnectionState) -> Unit = {},
    private val onSignalingUp: (Boolean) -> Unit = {},
    private val onBatteryUpdate: (Int) -> Unit = {},
    private val onCryAlert: () -> Unit = {},
) : WebRtcSession.Listener {

    private val selfId = UUID.randomUUID().toString().take(8)
    private val session = WebRtcSession(context, this)
    private val signaling = SignalingClient(selfId)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var batteryReceiver: BroadcastReceiver? = null
    private val iceRestarting = AtomicBoolean(false)

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
                Log.d(TAG, "Signaling ${if (up) "UP" else "DOWN"}, role=$role")
                if (up) {
                    when (role) {
                        ConnRole.BABY -> {
                            // Send retained offer so late-subscribing parent gets it immediately
                            session.createOffer { sdp ->
                                Log.d(TAG, "Baby: initial offer created")
                                signaling.send("offer", sdp.description, retained = true)
                            }
                            registerBatteryReceiver()
                        }
                        ConnRole.PARENT -> {
                            // Tell baby we're ready — baby will re-create a fresh offer+ICE
                            signaling.send("ping", "")
                            Log.d(TAG, "Parent: sent ping to baby")
                        }
                    }
                }
            }
        }
    }

    /** Baby side: report battery level to the parent on every system battery broadcast. */
    private fun registerBatteryReceiver() {
        if (role != ConnRole.BABY || batteryReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) sendBattery(level * 100 / scale)
            }
        }
        batteryReceiver = receiver
        val sticky = context.applicationContext
            .registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        // Emit the current level immediately from the sticky broadcast.
        if (sticky != null) {
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0 && scale > 0) sendBattery(level * 100 / scale)
        }
    }

    private fun onSignal(msg: SignalMessage) {
        when (msg.type) {
            "ping" -> if (role == ConnRole.BABY) {
                Log.d(TAG, "Baby: got ping, re-offering with fresh ICE")
                session.createOffer { sdp ->
                    Log.d(TAG, "Baby: fresh offer created for parent")
                    signaling.send("offer", sdp.description, retained = true)
                }
            }
            "offer" -> if (role == ConnRole.PARENT) {
                Log.d(TAG, "Parent: received offer, creating answer")
                session.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, msg.payload))
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
            "battery" -> if (role == ConnRole.PARENT) {
                msg.payload.toIntOrNull()?.let { onBatteryUpdate(it) }
            }
            "cry" -> if (role == ConnRole.PARENT) {
                CryNotifier.postCryAlert(context)
                onCryAlert()
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

    /** Baby: publish current battery percentage to the parent. */
    fun sendBattery(pct: Int) = signaling.send("battery", pct.toString())

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
        if (role != ConnRole.BABY) return
        when (state) {
            PeerConnection.IceConnectionState.FAILED -> scheduleIceRestart(delayMs = 1500)
            PeerConnection.IceConnectionState.DISCONNECTED -> scheduleIceRestart(delayMs = 4000)
            else -> {}
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

    private fun parseIce(payload: String): IceCandidate? = try {
        val o = JSONObject(payload)
        IceCandidate(o.getString("sdpMid"), o.getInt("sdpMLineIndex"), o.getString("candidate"))
    } catch (_: Exception) { null }

    companion object {
        private const val TAG = "BabyCam"
    }
}
