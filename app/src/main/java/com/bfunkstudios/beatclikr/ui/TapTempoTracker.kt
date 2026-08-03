package com.bfunkstudios.beatclikr.ui

import kotlin.math.roundToInt

enum class TapTempoFeedback { LISTENING, BUILDING, STEADY, RESET }

data class TapTempoResult(
    val bpm: Int?,
    val feedback: TapTempoFeedback
)

class TapTempoTracker {
    private val timestamps = ArrayDeque<Long>()

    fun record(elapsedRealtimeNanos: Long): TapTempoResult {
        val previous = timestamps.lastOrNull()
        if (previous != null) {
            val interval = elapsedRealtimeNanos - previous
            if (interval > RESET_NANOS) {
                timestamps.clear()
                timestamps.addLast(elapsedRealtimeNanos)
                return TapTempoResult(null, TapTempoFeedback.RESET)
            }
            if (interval < MIN_INTERVAL_NANOS) {
                return TapTempoResult(null, feedback())
            }
        }

        timestamps.addLast(elapsedRealtimeNanos)
        while (timestamps.size > MAX_TAPS) timestamps.removeFirst()
        if (timestamps.size < 2) return TapTempoResult(null, TapTempoFeedback.LISTENING)

        val intervals = timestamps.zipWithNext { first, second -> second - first }.sorted()
        val middle = intervals.size / 2
        val median = if (intervals.size % 2 == 0) {
            (intervals[middle - 1] + intervals[middle]) / 2L
        } else {
            intervals[middle]
        }
        val bpm = (NANOS_PER_MINUTE.toDouble() / median).roundToInt().coerceIn(MIN_BPM, MAX_BPM)
        return TapTempoResult(bpm, feedback())
    }

    private fun feedback() = when {
        timestamps.size >= 4 -> TapTempoFeedback.STEADY
        timestamps.size >= 2 -> TapTempoFeedback.BUILDING
        else -> TapTempoFeedback.LISTENING
    }

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 240
        const val MAX_TAPS = 8
        const val MIN_INTERVAL_NANOS = 250_000_000L
        const val RESET_NANOS = 2_000_000_000L
        const val NANOS_PER_MINUTE = 60_000_000_000L
    }
}
