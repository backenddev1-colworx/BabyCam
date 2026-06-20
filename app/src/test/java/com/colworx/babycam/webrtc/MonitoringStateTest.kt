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
