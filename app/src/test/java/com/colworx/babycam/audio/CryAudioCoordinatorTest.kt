package com.colworx.babycam.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class CryAudioCoordinatorTest {
    @Test
    fun noFeaturesEnabled_ownsNoMicrophoneCapture() {
        assertEquals(
            CryAudioOwner.NONE,
            CryAudioOwnershipPolicy.owner(cryEnabled = false, webRtcMicActive = false),
        )
    }

    @Test
    fun cryOnly_usesFallbackRecorder() {
        assertEquals(
            CryAudioOwner.FALLBACK,
            CryAudioOwnershipPolicy.owner(cryEnabled = true, webRtcMicActive = false),
        )
    }

    @Test
    fun webRtcMicActive_usesWebRtcSamplesForCryDetection() {
        assertEquals(
            CryAudioOwner.WEBRTC,
            CryAudioOwnershipPolicy.owner(cryEnabled = true, webRtcMicActive = true),
        )
        assertEquals(
            CryAudioOwner.WEBRTC,
            CryAudioOwnershipPolicy.owner(cryEnabled = false, webRtcMicActive = true),
        )
    }

    @Test
    fun coordinator_stopsFallbackBeforeWebRtcBecomesOwner() {
        val events = mutableListOf<String>()
        val coordinator = CryAudioCoordinator(
            startFallback = { events += "start-fallback" },
            stopFallback = { events += "stop-fallback" },
            consumeSamples = {},
        )

        coordinator.setCryEnabled(true)
        coordinator.setWebRtcMicActive(true)

        assertEquals(
            listOf("start-fallback", "stop-fallback"),
            events,
        )
        assertEquals(CryAudioOwner.WEBRTC, coordinator.owner)
    }

    @Test
    fun coordinator_resumesFallbackAfterWebRtcStopsWhenCryRemainsEnabled() {
        val events = mutableListOf<String>()
        val coordinator = CryAudioCoordinator(
            startFallback = { events += "start-fallback" },
            stopFallback = { events += "stop-fallback" },
            consumeSamples = {},
        )

        coordinator.setCryEnabled(true)
        coordinator.setWebRtcMicActive(true)
        events.clear()
        coordinator.setWebRtcMicActive(false)

        assertEquals(listOf("start-fallback"), events)
        assertEquals(CryAudioOwner.FALLBACK, coordinator.owner)
    }

    @Test
    fun pcmAccumulator_combinesWebRtcCallbacksIntoStableDetectionFrames() {
        val accumulator = PcmFrameAccumulator()
        val output = mutableListOf<ShortArray>()

        repeat(10) {
            output += accumulator.append(ShortArray(480) { it.toShort() }, targetSize = 4800)
        }

        assertEquals(1, output.size)
        assertEquals(4800, output.single().size)
    }
}
