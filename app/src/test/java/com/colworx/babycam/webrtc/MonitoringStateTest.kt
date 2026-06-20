package com.colworx.babycam.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MonitoringStateTest {

    @Test
    fun defaults_everyCapabilityOffAndHighQuality() {
        val state = MonitoringState()

        assertFalse(state.cameraEnabled)
        assertFalse(state.babyMicrophoneEnabled)
        assertFalse(state.torchEnabled)
        assertFalse(state.cryDetectionEnabled)
        assertFalse(state.lullabyPlaybackEnabled)
        assertFalse(state.parentCameraSharingEnabled)
        assertFalse(state.parentTalkEnabled)
        assertFalse(state.videoSaverEnabled)
    }

    @Test
    fun capabilityCommands_changeOnlyTheirIntendedCapability() {
        val active = allCapabilitiesActive()

        assertEquals(
            active.copy(cameraEnabled = false),
            active.transition(MonitoringCommand.SetCamera(false)),
        )
        assertEquals(
            active.copy(babyMicrophoneEnabled = false),
            active.transition(MonitoringCommand.SetBabyMicrophone(false)),
        )
        assertEquals(
            active.copy(torchEnabled = false),
            active.transition(MonitoringCommand.SetTorch(false)),
        )
        assertEquals(
            active.copy(cryDetectionEnabled = false),
            active.transition(MonitoringCommand.SetCryDetection(false)),
        )
        assertEquals(
            active.copy(lullabyPlaybackEnabled = false),
            active.transition(MonitoringCommand.SetLullabyPlayback(false)),
        )
        assertEquals(
            active.copy(parentCameraSharingEnabled = false),
            active.transition(MonitoringCommand.SetParentCameraSharing(false)),
        )
        assertEquals(
            active.copy(parentTalkEnabled = false),
            active.transition(MonitoringCommand.SetParentTalk(false)),
        )
        assertEquals(
            active.copy(videoSaverEnabled = false),
            active.transition(MonitoringCommand.SetVideoSaver(false)),
        )
        assertEquals(allCapabilitiesActive(), active)
    }

    @Test
    fun setCamera_canEnableOnlyCameraFromDefaults() {
        assertEquals(
            MonitoringState(cameraEnabled = true),
            MonitoringState().transition(MonitoringCommand.SetCamera(true)),
        )
    }

    @Test
    fun setBabyMicrophone_canEnableOnlyBabyMicrophoneFromDefaults() {
        assertEquals(
            MonitoringState(babyMicrophoneEnabled = true),
            MonitoringState().transition(MonitoringCommand.SetBabyMicrophone(true)),
        )
    }

    @Test
    fun setTorch_canEnableOnlyTorchFromDefaults() {
        assertEquals(
            MonitoringState(torchEnabled = true),
            MonitoringState().transition(MonitoringCommand.SetTorch(true)),
        )
    }

    @Test
    fun setCryDetection_canEnableOnlyCryDetectionFromDefaults() {
        assertEquals(
            MonitoringState(cryDetectionEnabled = true),
            MonitoringState().transition(MonitoringCommand.SetCryDetection(true)),
        )
    }

    @Test
    fun setLullabyPlayback_canEnableOnlyLullabyPlaybackFromDefaults() {
        assertEquals(
            MonitoringState(lullabyPlaybackEnabled = true),
            MonitoringState().transition(MonitoringCommand.SetLullabyPlayback(true)),
        )
    }

    @Test
    fun setParentCameraSharing_canEnableOnlyParentCameraSharingFromDefaults() {
        assertEquals(
            MonitoringState(parentCameraSharingEnabled = true),
            MonitoringState().transition(MonitoringCommand.SetParentCameraSharing(true)),
        )
    }

    @Test
    fun setParentTalk_canEnableOnlyParentTalkFromDefaults() {
        assertEquals(
            MonitoringState(parentTalkEnabled = true),
            MonitoringState().transition(MonitoringCommand.SetParentTalk(true)),
        )
    }

    @Test
    fun setVideoSaver_canEnableOnlyVideoSaverFromDefaults() {
        assertEquals(
            MonitoringState(videoSaverEnabled = true),
            MonitoringState().transition(MonitoringCommand.SetVideoSaver(true)),
        )
    }

    @Test
    fun parentSessionInitialState_keepsCameraAndMicOffWhenLegacyMicFlagIsTrue() {
        val state = MonitoringSessionDefaults.initial(initialMicOn = true)

        assertFalse(state.cameraEnabled)
        assertFalse(state.babyMicrophoneEnabled)
    }

    @Test
    fun resetState_keepsCameraAndMicOff() {
        val state = MonitoringSessionDefaults.reset()

        assertFalse(state.cameraEnabled)
        assertFalse(state.babyMicrophoneEnabled)
    }

    @Test
    fun parentDisconnected_turnsActiveCapabilitiesOffAndPreservesQualityPreference() {
        val result = allCapabilitiesActive()
            .transition(MonitoringCommand.ParentDisconnected)

        assertEquals(failSafeState(videoSaverEnabled = true), result)
    }

    @Test
    fun controlLeaseExpired_turnsActiveCapabilitiesOffAndPreservesQualityPreference() {
        val result = allCapabilitiesActive()
            .transition(MonitoringCommand.ControlLeaseExpired)

        assertEquals(failSafeState(videoSaverEnabled = true), result)
    }

    private fun allCapabilitiesActive() = MonitoringState(
        cameraEnabled = true,
        babyMicrophoneEnabled = true,
        torchEnabled = true,
        cryDetectionEnabled = true,
        lullabyPlaybackEnabled = true,
        parentCameraSharingEnabled = true,
        parentTalkEnabled = true,
        videoSaverEnabled = true,
    )

    private fun failSafeState(videoSaverEnabled: Boolean) = MonitoringState(
        videoSaverEnabled = videoSaverEnabled,
    )
}
