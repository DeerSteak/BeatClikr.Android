package com.bfunkstudios.beatclikr.services

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderDurationHistogramTest {

    @Test
    fun emptyHistogramReportsZeros() {
        assertEquals(
            RenderDurationPercentiles(0, 0, 0, 0),
            RenderDurationHistogram().percentiles()
        )
    }

    @Test
    fun reportsBoundedPercentilesAndExactMaximum() {
        val histogram = RenderDurationHistogram()
        repeat(50) { histogram.record(50_000) }
        repeat(45) { histogram.record(150_000) }
        repeat(4) { histogram.record(250_000) }
        histogram.record(20_000_000)

        assertEquals(
            RenderDurationPercentiles(
                p50UpperBoundNanos = 50_000,
                p95UpperBoundNanos = 150_000,
                p99UpperBoundNanos = 250_000,
                maximumNanos = 20_000_000
            ),
            histogram.percentiles()
        )
    }

    @Test
    fun resetDropsAllPriorSamples() {
        val histogram = RenderDurationHistogram()
        histogram.record(1_000_000)

        histogram.reset()

        assertEquals(
            RenderDurationPercentiles(0, 0, 0, 0),
            histogram.percentiles()
        )
    }
}
