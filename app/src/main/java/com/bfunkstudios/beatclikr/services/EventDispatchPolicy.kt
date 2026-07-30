package com.bfunkstudios.beatclikr.services

internal fun expiredEventCount(
    nowNanos: Long,
    intendedEventNanos: Long,
    lookaheadNanos: Long,
    eventIntervalNanos: Long
): Long {
    require(lookaheadNanos >= 0) { "Lookahead must not be negative" }
    require(eventIntervalNanos > 0) { "Event interval must be positive" }
    val lateNanos = nowNanos - (intendedEventNanos - lookaheadNanos)
    return if (lateNanos < eventIntervalNanos) 0 else lateNanos / eventIntervalNanos
}
