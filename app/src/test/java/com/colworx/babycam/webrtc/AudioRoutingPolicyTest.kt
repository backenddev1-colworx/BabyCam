package com.colworx.babycam.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutingPolicyTest {
    @Test
    fun activeAudioRoutingTarget_usesCommunicationModeAndMaxVolume() {
        val original = AudioRoutingState(mode = 7, speakerphoneOn = false, voiceCallVolume = 2)

        val active = activeAudioRoutingTarget(
            original = original,
            communicationMode = 3,
            maxVoiceCallVolume = 9,
        )

        assertEquals(3, active.mode)
        assertTrue(active.speakerphoneOn)
        assertEquals(9, active.voiceCallVolume)
    }

    @Test
    fun restoreAudioRoutingTarget_returnsExactOriginalState() {
        val original = AudioRoutingState(mode = 5, speakerphoneOn = true, voiceCallVolume = 4)

        assertEquals(original, restoreAudioRoutingTarget(original))
    }
}
