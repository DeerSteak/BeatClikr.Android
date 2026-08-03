package com.bfunkstudios.beatclikr.services

data class LocalDiagnosticSnapshot(
    val appVersion: String,
    val buildCode: Int,
    val device: String,
    val osVersion: String,
    val metrics: FrameAudioMetricsSnapshot?,
    val transitions: List<PlaybackLifecycleDiagnostic>
)

object LocalDiagnostics {
    private const val MAX_TRANSITIONS = 20

    fun render(snapshot: LocalDiagnosticSnapshot): String = buildString {
        appendLine("BeatClikr diagnostics v1")
        appendLine("app=${sanitizeMetadata(snapshot.appVersion)} (${snapshot.buildCode})")
        appendLine("device=${sanitizeMetadata(snapshot.device)}")
        appendLine("os=${sanitizeMetadata(snapshot.osVersion)}")
        val metrics = snapshot.metrics
        if (metrics == null) {
            appendLine("audio=unavailable")
        } else {
            appendLine("route=${metrics.route}")
            appendLine("backend=${metrics.backend}")
            appendLine("stream=${metrics.sampleRate}Hz/${metrics.channelCount}ch/${metrics.bufferFrames}frames")
            appendLine("latency_ns=${metrics.estimatedOutputLatencyNanos}")
            appendLine("latency_confidence=${if (metrics.frameCorrelation == null) "estimated" else "timestamp_correlated"}")
            appendLine("underruns=${metrics.underrunCount}")
            appendLine("drops=${metrics.droppedEvents}")
            appendLine("deadline_misses=${metrics.deadlineMisses}")
            appendLine("backend_failure=${metrics.latestBackendFailure?.code ?: "none"}")
        }
        appendLine("recent_transitions:")
        snapshot.transitions.takeLast(MAX_TRANSITIONS).forEach {
            appendLine("${it.sequence}:${safeState(it.fromState)}>${safeState(it.toState)}")
        }
    }

    private fun sanitizeMetadata(value: String): String =
        value.replace(Regex("[^A-Za-z0-9 ._()-]"), "?").take(120)

    private fun safeState(value: String): String = value.takeIf { it in SAFE_STATES } ?: "Unknown"

    private val SAFE_STATES = setOf(
        "Idle",
        "Preparing",
        "Starting",
        "Playing",
        "Stopping",
        "Interrupted",
        "Failed",
        "Unknown"
    )
}
