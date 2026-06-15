package com.colworx.babycam.webrtc

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.colworx.babycam.media.FrameCapture
import com.colworx.babycam.media.Snapshot
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

/**
 * App-wide holder for the single active connection so Compose screens can observe live state
 * without threading a ViewModel through navigation. Baby and Parent flows both go through here.
 */
object LiveSession {
    var connection: BabyCamConnection? = null
        private set
    var room: String = ""
        private set

    val remoteVideo = mutableStateOf<VideoTrack?>(null)
    val connState = mutableStateOf<PeerConnection.IceConnectionState?>(null)
    val signalingUp = mutableStateOf(false)

    /** Parent-observed baby battery percent; null when unknown. */
    val babyBattery = mutableStateOf<Int?>(null)

    /** Bumped each time a cry alert is received so parent UI can react. */
    val cryPing = mutableStateOf(0L)

    /** Remote control state — parent controls these, baby obeys. */
    val babyCamEnabled = mutableStateOf(true)
    val babyMicEnabled = mutableStateOf(true)
    val babyTorchOn = mutableStateOf(false)

    fun startBaby(context: Context, room: String) {
        stop()
        this.room = room
        connection = BabyCamConnection(
            context.applicationContext, ConnRole.BABY, room,
            onRemoteVideo = {},
            onState = { connState.value = it },
            onSignalingUp = { signalingUp.value = it },
            onBatteryUpdate = { babyBattery.value = it },
            onCryAlert = { cryPing.value = cryPing.value + 1 },
        ).also { it.start() }
    }

    fun startParent(context: Context, room: String) {
        stop()
        this.room = room
        connection = BabyCamConnection(
            context.applicationContext, ConnRole.PARENT, room,
            onRemoteVideo = { remoteVideo.value = it },
            onState = { connState.value = it },
            onSignalingUp = { signalingUp.value = it },
            onBatteryUpdate = { babyBattery.value = it },
            onCryAlert = { cryPing.value = cryPing.value + 1 },
        ).also { it.start() }
    }

    fun setTalking(on: Boolean) = connection?.setTalking(on) ?: Unit
    fun switchCamera() = connection?.switchCamera() ?: Unit
    fun sendLullaby(sound: String) = connection?.sendLullaby(sound) ?: Unit

    fun setRemoteCamera(on: Boolean) {
        babyCamEnabled.value = on
        connection?.setRemoteCamera(on)
    }

    fun setRemoteMic(on: Boolean) {
        babyMicEnabled.value = on
        connection?.setRemoteMic(on)
    }

    fun setTorch(on: Boolean) {
        babyTorchOn.value = on
        connection?.setTorch(on)
    }

    fun setVideoEnabled(enabled: Boolean) {
        babyCamEnabled.value = enabled
        connection?.setVideoEnabled(enabled)
    }

    /** Baby: publish a cry alert to the paired parent. */
    fun sendCry() = connection?.sendCry() ?: Unit

    /**
     * Captures the current incoming (remote) video frame and saves it to the gallery.
     * Invokes [onResult] with true on success. The callback may run off the main thread;
     * callers showing UI (e.g. a Toast) are responsible for posting back to the main thread.
     */
    fun captureSnapshot(context: Context, onResult: (Boolean) -> Unit) {
        val track = remoteVideo.value
        if (track == null) {
            onResult(false)
            return
        }
        FrameCapture.capture(track) { bmp ->
            if (bmp != null) {
                val uri = Snapshot.saveToGallery(context.applicationContext, bmp)
                onResult(uri != null)
            } else {
                onResult(false)
            }
        }
    }

    fun stop() {
        connection?.stop()
        connection = null
        remoteVideo.value = null
        connState.value = null
        signalingUp.value = false
        babyBattery.value = null
        babyCamEnabled.value = true
        babyMicEnabled.value = true
        babyTorchOn.value = false
    }
}
