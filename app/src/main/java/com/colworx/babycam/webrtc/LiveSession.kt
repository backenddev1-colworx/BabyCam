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
    private val initialMonitoringState = MonitoringSessionDefaults.initial()

    var connection: BabyCamConnection? = null
        private set
    var room: String = ""
        private set

    val remoteVideo = mutableStateOf<VideoTrack?>(null)

    /** Baby-observed own camera preview track; null until the camera capturer is up. */
    val localVideo = mutableStateOf<VideoTrack?>(null)

    val connState = mutableStateOf<PeerConnection.IceConnectionState?>(null)
    val signalingUp = mutableStateOf(false)

    /** Parent-observed baby battery percent; null when unknown. */
    val babyBattery = mutableStateOf<Int?>(null)

    /** Bumped each time a cry alert is received so parent UI can react. */
    val cryPing = mutableStateOf(0L)

    /** Remote control state — parent controls these, baby obeys. */
    val babyCamEnabled = mutableStateOf(initialMonitoringState.cameraEnabled)
    val babyMicEnabled = mutableStateOf(initialMonitoringState.babyMicrophoneEnabled)
    val babyTorchOn = mutableStateOf(false)

    /** Parent-observed: whether the baby's cry detection is currently ON (synced via "cry_state"). */
    val babyCryDetectionEnabled = mutableStateOf(false)

    /** Parent-observed: whether the baby's capture is in battery-saver (low-res) mode. */
    val babyVideoSaver = mutableStateOf(false)

    /** Parent's own state: whether the parent is currently sharing its camera back to the baby. */
    val parentSharingCamera = mutableStateOf(false)

    /** Baby-observed: whether the parent is sharing its camera (drives the PiP on the baby screen). */
    val parentCamSharing = mutableStateOf(false)

    fun startBaby(context: Context, room: String) {
        stop()
        this.room = room
        connection = BabyCamConnection(
            context.applicationContext, ConnRole.BABY, room,
            // Baby receives the parent's camera (on-demand two-way) on this track.
            onRemoteVideo = { remoteVideo.value = it },
            onLocalVideo = { localVideo.value = it },
            onState = { connState.value = it },
            onSignalingUp = { signalingUp.value = it },
            onBatteryUpdate = { babyBattery.value = it },
            onCryAlert = { cryPing.value = cryPing.value + 1 },
        ).also { it.start() }
    }

    /**
     * [initialMicOn] is retained for call-site compatibility only. New sessions always begin with
     * the baby microphone OFF and require an explicit parent command to enable it.
     */
    fun startParent(context: Context, room: String, initialMicOn: Boolean = false) {
        stop()
        this.room = room
        val initialState = MonitoringSessionDefaults.initial(initialMicOn)
        babyCamEnabled.value = initialState.cameraEnabled
        babyMicEnabled.value = initialState.babyMicrophoneEnabled
        val conn = BabyCamConnection(
            context.applicationContext, ConnRole.PARENT, room,
            onRemoteVideo = { remoteVideo.value = it },
            onState = { connState.value = it },
            onSignalingUp = { signalingUp.value = it },
            onBatteryUpdate = { babyBattery.value = it },
            onCryAlert = { cryPing.value = cryPing.value + 1 },
            onTorchState = { babyTorchOn.value = it },
        )
        connection = conn
        conn.start()
    }

    fun setTalking(on: Boolean) = connection?.setTalking(on) ?: Unit

    /** Parent: ask the baby phone to flip its own front/back camera. */
    fun switchCamera() = connection?.requestRemoteCameraSwitch() ?: Unit
    fun sendLullaby(sound: String) = connection?.sendLullaby(sound) ?: Unit

    /**
     * Parent: turn the baby's camera fully on/off. Off is a real power-save standby — the baby's
     * Camera2 capturer is stopped, not just the outgoing track muted — so the baby phone actually
     * stops drawing power/heating up while off (see WebRtcSession.setCameraStandby). The mic is
     * independent of this, so audio-only listening with the camera off is just: cam off + mic on.
     */
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

    /** Parent: turn the baby's cry detection on/off remotely (baby confirms via "cry_state"). */
    fun setRemoteCryDetection(on: Boolean) {
        babyCryDetectionEnabled.value = on // optimistic; corrected by the baby's echo
        connection?.setRemoteCryDetection(on)
    }

    /** Parent: switch the baby's capture between battery-saver (low-res) and high quality. */
    fun setRemoteQuality(saver: Boolean) {
        babyVideoSaver.value = saver
        connection?.setRemoteQuality(saver)
    }

    /** Parent: start/stop sharing the parent's own camera back to the baby (on-demand two-way). */
    fun setParentCameraSharing(on: Boolean) {
        parentSharingCamera.value = on
        connection?.setParentCameraSharing(on)
    }

    /** Baby: notify the parent that cry detection was toggled locally (keeps the parent UI synced). */
    fun notifyCryStateChanged(on: Boolean) {
        connection?.sendCryState(on)
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
        val resetState = MonitoringSessionDefaults.reset()
        connection?.stop()
        connection = null
        remoteVideo.value = null
        localVideo.value = null
        connState.value = null
        signalingUp.value = false
        babyBattery.value = null
        babyCamEnabled.value = resetState.cameraEnabled
        babyMicEnabled.value = resetState.babyMicrophoneEnabled
        babyTorchOn.value = false
        babyCryDetectionEnabled.value = false
        babyVideoSaver.value = false
        parentSharingCamera.value = false
        parentCamSharing.value = false
    }
}
