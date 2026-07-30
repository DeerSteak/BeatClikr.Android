package com.bfunkstudios.beatclikr.services

data class RenderDurationPercentiles(
    val p50UpperBoundNanos: Long,
    val p95UpperBoundNanos: Long,
    val p99UpperBoundNanos: Long,
    val maximumNanos: Long
)

/** Fixed-memory duration histogram with allocation-free recording. */
internal class RenderDurationHistogram {
    private val upperBounds = buildUpperBounds()
    private val buckets = LongArray(upperBounds.size)
    private var samples = 0L
    private var maximumNanos = 0L

    fun record(durationNanos: Long) {
        val bounded = durationNanos.coerceAtLeast(0L)
        var low = 0
        var high = upperBounds.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (bounded <= upperBounds[middle]) high = middle else low = middle + 1
        }
        buckets[low]++
        samples++
        if (bounded > maximumNanos) maximumNanos = bounded
    }

    fun reset() {
        buckets.fill(0L)
        samples = 0L
        maximumNanos = 0L
    }

    fun percentiles(): RenderDurationPercentiles = RenderDurationPercentiles(
        p50UpperBoundNanos = percentileUpperBound(50),
        p95UpperBoundNanos = percentileUpperBound(95),
        p99UpperBoundNanos = percentileUpperBound(99),
        maximumNanos = maximumNanos
    )

    private fun percentileUpperBound(percent: Int): Long {
        if (samples == 0L) return 0L
        val target = (samples * percent + 99L) / 100L
        var cumulative = 0L
        var index = 0
        while (index < buckets.size) {
            cumulative += buckets[index]
            if (cumulative >= target) return upperBounds[index]
            index++
        }
        return maximumNanos
    }

    private fun buildUpperBounds(): LongArray {
        val bounds = LongArray(BUCKET_COUNT)
        var index = 0
        while (index < LINEAR_BUCKETS) {
            bounds[index] = (index + 1L) * LINEAR_BUCKET_NANOS
            index++
        }
        while (index < bounds.lastIndex) {
            bounds[index] = Math.multiplyExact(bounds[index - 1], 5L) / 4L
            index++
        }
        bounds[bounds.lastIndex] = Long.MAX_VALUE
        return bounds
    }

    private companion object {
        const val BUCKET_COUNT = 128
        const val LINEAR_BUCKETS = 100
        const val LINEAR_BUCKET_NANOS = 5_000L
    }
}
