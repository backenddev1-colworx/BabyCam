package com.colworx.babycam.webrtc

import android.content.Context
import androidx.compose.runtime.mutableStateOf
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

    fun startBaby(context: Context, room: String) {
        stop()
        this.room = room
        connection = BabyCamConnection(
            context.applicationContext, ConnRole.BABY, room,
            onRemoteVideo = {},
            onState = { connState.value = it },
            onSignalingUp = { signalingUp.value = it },
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
        ).also { it.start() }
    }

    fun setTalking(on: Boolean) = connection?.setTalking(on) ?: Unit
    fun switchCamera() = connection?.switchCamera() ?: Unit
    fun sendLullaby(sound: String) = connection?.sendLullaby(sound) ?: Unit
    fun setVideoEnabled(enabled: Boolean) = connection?.setVideoEnabled(enabled) ?: Unit

    fun stop() {
        connection?.stop()
        connection = null
        remoteVideo.value = null
        connState.value = null
        signalingUp.value = false
    }
}
