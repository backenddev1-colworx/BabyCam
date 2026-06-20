package com.colworx.babycam.webrtc

class BatteryPublisher(
    private val publish: (Int) -> Unit,
) {
    private var currentPercentage: Int? = null

    @Synchronized
    fun observe(level: Int, scale: Int) {
        val percentage = percentage(level, scale) ?: return
        if (percentage == currentPercentage) return

        currentPercentage = percentage
        publish(percentage)
    }

    @Synchronized
    fun forceCurrent() {
        currentPercentage?.let(publish)
    }

    private fun percentage(level: Int, scale: Int): Int? {
        if (level < 0 || scale <= 0) return null
        return ((level.toLong() * 100L) / scale)
            .coerceIn(0L, 100L)
            .toInt()
    }
}
