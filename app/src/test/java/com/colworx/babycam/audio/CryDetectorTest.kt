package com.colworx.babycam.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryDetectorTest {

    private val frameSize = 1024
    private val sustainedFrames = 5

    private fun frame(amplitude: Short): ShortArray = ShortArray(frameSize) { amplitude }

    // RMS of a constant-amplitude frame equals that amplitude exactly,
    // so a fill of value V gives rms == V. This keeps the thresholds easy to reason about.

    @Test
    fun rms_emptyArray_isZero() {
        val detector = CryDetector()
        assertEquals(0.0, detector.rms(ShortArray(0)), 0.0001)
    }

    @Test
    fun rms_constantFrame_equalsAmplitude() {
        val detector = CryDetector()
        assertEquals(8000.0, detector.rms(frame(8000)), 0.0001)
    }

    @Test
    fun silence_neverFires() {
        val detector = CryDetector(Sensitivity.HIGH, sustainedFrames)
        val silence = frame(0)
        repeat(sustainedFrames * 4) {
            assertFalse(detector.process(silence))
        }
    }

    @Test
    fun sustainedLoudTone_firesOnNthFrameNotBefore() {
        val detector = CryDetector(Sensitivity.MEDIUM, sustainedFrames)
        // amplitude 8000 >> MEDIUM threshold (2500)
        val loud = frame(8000)
        for (i in 1 until sustainedFrames) {
            assertFalse("Should not fire on frame $i", detector.process(loud))
        }
        assertTrue("Should fire on frame $sustainedFrames", detector.process(loud))
    }

    @Test
    fun firesThenResets_canRetrigger() {
        val detector = CryDetector(Sensitivity.MEDIUM, sustainedFrames)
        val loud = frame(8000)
        // First sustained cry.
        for (i in 1 until sustainedFrames) assertFalse(detector.process(loud))
        assertTrue(detector.process(loud))
        // Counter reset after firing; needs a fresh full run to fire again.
        for (i in 1 until sustainedFrames) assertFalse(detector.process(loud))
        assertTrue(detector.process(loud))
    }

    @Test
    fun singleLoudSpike_thenSilence_doesNotFire() {
        val detector = CryDetector(Sensitivity.MEDIUM, sustainedFrames)
        assertFalse(detector.process(frame(8000)))
        val silence = frame(0)
        repeat(sustainedFrames * 2) {
            assertFalse(detector.process(silence))
        }
    }

    @Test
    fun moderateSignal_firesOnHigh_butNotOnMedium() {
        // amplitude 2000 is between HIGH (1400) and MEDIUM (2500).
        val moderate = frame(2000)

        val mediumDetector = CryDetector(Sensitivity.MEDIUM, sustainedFrames)
        repeat(sustainedFrames * 2) {
            assertFalse("MEDIUM must not fire for amplitude 2000", mediumDetector.process(moderate))
        }

        val highDetector = CryDetector(Sensitivity.HIGH, sustainedFrames)
        for (i in 1 until sustainedFrames) {
            assertFalse(highDetector.process(moderate))
        }
        assertTrue("HIGH must fire for sustained amplitude 2000", highDetector.process(moderate))
    }

    @Test
    fun reset_clearsProgress() {
        val detector = CryDetector(Sensitivity.MEDIUM, sustainedFrames)
        val loud = frame(8000)
        for (i in 1 until sustainedFrames) assertFalse(detector.process(loud))
        detector.reset()
        // After reset, the next frame is frame 1 again, so it must not fire immediately.
        assertFalse(detector.process(loud))
    }
}
