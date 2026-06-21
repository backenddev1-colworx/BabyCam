package com.colworx.babycam.webrtc

data class AudioRoutingState(
    val mode: Int,
    val speakerphoneOn: Boolean,
    val voiceCallVolume: Int,
)

fun activeAudioRoutingTarget(
    original: AudioRoutingState,
    communicationMode: Int,
    maxVoiceCallVolume: Int,
): AudioRoutingState = original.copy(
    mode = communicationMode,
    speakerphoneOn = true,
    voiceCallVolume = maxVoiceCallVolume,
)

fun restoreAudioRoutingTarget(original: AudioRoutingState): AudioRoutingState = original
