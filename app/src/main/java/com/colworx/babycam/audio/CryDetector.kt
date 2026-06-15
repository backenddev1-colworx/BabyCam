package com.colworx.babycam.audio

import kotlin.math.sqrt

/**
 * Cry/sound detection sensitivity levels.
 *
 * Each level maps to an RMS amplitude threshold (for 16-bit PCM samples,
 * range -32768..32767). A lower threshold means the detector fires for
 * quieter sounds, i.e. higher sensitivity.
 */
enum class Sensitivity(val rmsThreshold: Double) {
    LOW(4000.0),
    MEDIUM(2500.0),
    HIGH(1400.0)
}

/**
 * Pure, framework-free cry detector designed for JVM unit testing.
 *
 * Audio is fed in as frames of 16-bit PCM samples via [process]. A cry is
 * reported only when the RMS energy stays at/above the configured threshold
 * for [sustainedFrames] consecutive frames, filtering out brief spikes.
 */
class CryDetector(
    var sensitivity: Sensitivity = Sensitivity.MEDIUM,
    private val sustainedFrames: Int = 5
) {
    private var aboveCount = 0

    /**
     * Root-mean-square amplitude of [samples].
     * Returns 0.0 for an empty array.
     */
    fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (s in samples) {
            val v = s.toDouble()
            sumSquares += v * v
        }
        return sqrt(sumSquares / samples.size)
    }

    /**
     * Feed one frame of audio.
     *
     * @return true exactly when [sustainedFrames] consecutive frames have met
     *   or exceeded the current sensitivity threshold. Resets the internal
     *   counter after firing so it can re-trigger on a later sustained cry.
     */
    fun process(samples: ShortArray): Boolean {
        val level = rms(samples)
        if (level >= sensitivity.rmsThreshold) {
            aboveCount++
            if (aboveCount >= sustainedFrames) {
                aboveCount = 0
                return true
            }
        } else {
            aboveCount = 0
        }
        return false
    }

    /** Reset the consecutive-frame counter. */
    fun reset() {
        aboveCount = 0
    }
}
