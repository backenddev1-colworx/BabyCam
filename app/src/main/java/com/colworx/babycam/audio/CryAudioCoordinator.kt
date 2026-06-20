package com.colworx.babycam.audio

enum class CryAudioOwner {
    NONE,
    FALLBACK,
    WEBRTC,
}

object CryAudioOwnershipPolicy {
    fun owner(cryEnabled: Boolean, webRtcMicActive: Boolean): CryAudioOwner = when {
        webRtcMicActive -> CryAudioOwner.WEBRTC
        cryEnabled -> CryAudioOwner.FALLBACK
        else -> CryAudioOwner.NONE
    }
}

class PcmFrameAccumulator {
    private var targetSize = 0
    private var buffer = ShortArray(0)
    private var count = 0

    @Synchronized
    fun append(samples: ShortArray, targetSize: Int): List<ShortArray> {
        if (targetSize <= 0 || samples.isEmpty()) return emptyList()
        if (this.targetSize != targetSize) {
            this.targetSize = targetSize
            buffer = ShortArray(targetSize)
            count = 0
        }

        val frames = mutableListOf<ShortArray>()
        var sourceOffset = 0
        while (sourceOffset < samples.size) {
            val copyCount = minOf(targetSize - count, samples.size - sourceOffset)
            samples.copyInto(
                destination = buffer,
                destinationOffset = count,
                startIndex = sourceOffset,
                endIndex = sourceOffset + copyCount,
            )
            count += copyCount
            sourceOffset += copyCount
            if (count == targetSize) {
                frames += buffer.copyOf()
                count = 0
            }
        }
        return frames
    }
}

class CryAudioCoordinator(
    private val startFallback: () -> Unit,
    private val stopFallback: () -> Unit,
    private val consumeSamples: (ShortArray) -> Unit,
) {
    private var cryEnabled = false
    private var webRtcMicActive = false

    var owner: CryAudioOwner = CryAudioOwner.NONE
        private set

    @Synchronized
    fun setCryEnabled(enabled: Boolean) {
        cryEnabled = enabled
        updateOwner()
    }

    @Synchronized
    fun setWebRtcMicActive(active: Boolean) {
        webRtcMicActive = active
        updateOwner()
    }

    @Synchronized
    fun submitFallbackSamples(samples: ShortArray) {
        if (owner == CryAudioOwner.FALLBACK && cryEnabled) consumeSamples(samples)
    }

    @Synchronized
    fun submitWebRtcSamples(samples: ShortArray) {
        if (owner == CryAudioOwner.WEBRTC && cryEnabled) consumeSamples(samples)
    }

    private fun updateOwner() {
        val next = CryAudioOwnershipPolicy.owner(cryEnabled, webRtcMicActive)
        if (next == owner) return
        if (owner == CryAudioOwner.FALLBACK) stopFallback()
        owner = next
        if (next == CryAudioOwner.FALLBACK) startFallback()
    }
}

object CryAudioBridge {
    private var coordinator: CryAudioCoordinator? = null
    private var webRtcMicActive = false

    @Synchronized
    fun attach(value: CryAudioCoordinator) {
        coordinator = value
        value.setWebRtcMicActive(webRtcMicActive)
    }

    @Synchronized
    fun detach(value: CryAudioCoordinator) {
        if (coordinator === value) coordinator = null
    }

    @Synchronized
    fun beforeWebRtcMicEnabled() {
        webRtcMicActive = true
        coordinator?.setWebRtcMicActive(true)
    }

    @Synchronized
    fun afterWebRtcMicDisabled() {
        webRtcMicActive = false
        coordinator?.setWebRtcMicActive(false)
    }

    @Synchronized
    fun submitWebRtcSamples(samples: ShortArray) = coordinator?.submitWebRtcSamples(samples)
}
