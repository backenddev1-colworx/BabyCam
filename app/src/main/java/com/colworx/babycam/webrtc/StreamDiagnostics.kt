package com.colworx.babycam.webrtc

data class ParentStreamDiagnostics(
    val resolutionLabel: String = "--",
    val fpsLabel: String = "--",
    val bitrateLabel: String = "--",
    val packetLossLabel: String = "--",
    val icePathLabel: String = "Unknown",
    val signalingLabel: String = "--",
    val connectionLabel: String = "--",
    val qualityModeLabel: String = "High",
) {
    fun compactSummary(): String {
        val parts = listOf(resolutionLabel, fpsLabel, icePathLabel)
            .filter { it.isNotBlank() && it != "--" }
        return if (parts.isEmpty()) "Diagnostics" else parts.joinToString(" • ")
    }
}

fun diagnosticsIcePath(localCandidateType: String?, remoteCandidateType: String?): String {
    val local = localCandidateType?.lowercase()
    val remote = remoteCandidateType?.lowercase()
    return when {
        local == "relay" || remote == "relay" -> "Relay"
        local != null || remote != null -> "Direct"
        else -> "Unknown"
    }
}
