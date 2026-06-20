package com.colworx.babycam.webrtc

data class MonitoringState(
    val cameraEnabled: Boolean = false,
    val babyMicrophoneEnabled: Boolean = false,
    val torchEnabled: Boolean = false,
    val cryDetectionEnabled: Boolean = false,
    val lullabyPlaybackEnabled: Boolean = false,
    val parentCameraSharingEnabled: Boolean = false,
    val parentTalkEnabled: Boolean = false,
    val videoSaverEnabled: Boolean = false,
) {
    fun transition(command: MonitoringCommand): MonitoringState = when (command) {
        is MonitoringCommand.SetCamera -> copy(cameraEnabled = command.enabled)
        is MonitoringCommand.SetBabyMicrophone -> copy(babyMicrophoneEnabled = command.enabled)
        is MonitoringCommand.SetTorch -> copy(torchEnabled = command.enabled)
        is MonitoringCommand.SetCryDetection -> copy(cryDetectionEnabled = command.enabled)
        is MonitoringCommand.SetLullabyPlayback -> copy(lullabyPlaybackEnabled = command.enabled)
        is MonitoringCommand.SetParentCameraSharing ->
            copy(parentCameraSharingEnabled = command.enabled)
        is MonitoringCommand.SetParentTalk -> copy(parentTalkEnabled = command.enabled)
        is MonitoringCommand.SetVideoSaver -> copy(videoSaverEnabled = command.enabled)
        MonitoringCommand.ParentDisconnected,
        MonitoringCommand.ControlLeaseExpired,
        -> failSafeOff()
    }

    fun failSafeOff(): MonitoringState = copy(
        cameraEnabled = false,
        babyMicrophoneEnabled = false,
        torchEnabled = false,
        cryDetectionEnabled = false,
        lullabyPlaybackEnabled = false,
        parentCameraSharingEnabled = false,
        parentTalkEnabled = false,
    )
}

sealed interface MonitoringCommand {
    data class SetCamera(val enabled: Boolean) : MonitoringCommand
    data class SetBabyMicrophone(val enabled: Boolean) : MonitoringCommand
    data class SetTorch(val enabled: Boolean) : MonitoringCommand
    data class SetCryDetection(val enabled: Boolean) : MonitoringCommand
    data class SetLullabyPlayback(val enabled: Boolean) : MonitoringCommand
    data class SetParentCameraSharing(val enabled: Boolean) : MonitoringCommand
    data class SetParentTalk(val enabled: Boolean) : MonitoringCommand
    data class SetVideoSaver(val enabled: Boolean) : MonitoringCommand
    data object ParentDisconnected : MonitoringCommand
    data object ControlLeaseExpired : MonitoringCommand
}

data class MonitoringSessionState(
    val babyCamEnabled: Boolean = false,
    val babyMicEnabled: Boolean = false,
    val babyTorchOn: Boolean = false,
    val babyCryDetectionEnabled: Boolean = false,
    val babyLullabyPlaying: Boolean = false,
    val babyVideoSaver: Boolean = false,
    val parentSharingCamera: Boolean = false,
    val parentCamSharing: Boolean = false,
    val parentTalking: Boolean = false,
)

object MonitoringSessionDefaults {
    @Suppress("UNUSED_PARAMETER")
    fun initial(initialMicOn: Boolean = false): MonitoringSessionState = MonitoringSessionState()

    fun reset(): MonitoringSessionState = MonitoringSessionState()
}
