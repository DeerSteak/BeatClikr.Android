package com.bfunkstudios.beatclikr.ui

sealed interface PracticeDurationDisplay {
    data class HoursMinutes(val hours: Long, val minutes: Long) : PracticeDurationDisplay
    data class MinutesSeconds(val minutes: Long, val seconds: Long) : PracticeDurationDisplay
    data class Seconds(val seconds: Long) : PracticeDurationDisplay
}

object PracticeDurationFormatter {
    fun components(durationNanos: Long): PracticeDurationDisplay {
        val totalSeconds = durationNanos.coerceAtLeast(0) / NANOS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return when {
            hours > 0 -> PracticeDurationDisplay.HoursMinutes(hours, minutes)
            minutes > 0 -> PracticeDurationDisplay.MinutesSeconds(minutes, seconds)
            else -> PracticeDurationDisplay.Seconds(seconds)
        }
    }

    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
}
