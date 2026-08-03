package com.bfunkstudios.beatclikr.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTrackerTest {
    private val tracker = TapTempoTracker()

    @Test
    fun `accidental double tap is ignored`() {
        tracker.record(1_000_000_000L)

        val result = tracker.record(1_100_000_000L)

        assertNull(result.bpm)
        assertEquals(TapTempoFeedback.LISTENING, result.feedback)
    }

    @Test
    fun `median rejects one tempo jump`() {
        listOf(0L, 500L, 1_000L, 1_750L, 2_250L).forEach {
            tracker.record(it * 1_000_000L)
        }

        assertEquals(120, tracker.record(2_750_000_000L).bpm)
    }

    @Test
    fun `stale interval resets the sequence`() {
        tracker.record(0L)
        tracker.record(500_000_000L)

        val result = tracker.record(3_000_000_001L)

        assertNull(result.bpm)
        assertEquals(TapTempoFeedback.RESET, result.feedback)
    }

    @Test
    fun `sparse valid taps produce whole bpm`() {
        tracker.record(0L)

        val result = tracker.record(1_500_000_000L)

        assertEquals(40, result.bpm)
    }

    @Test
    fun `nonmonotonic timestamp is rejected without corrupting elapsed intervals`() {
        tracker.record(1_000_000_000L)
        tracker.record(900_000_000L)

        val result = tracker.record(1_500_000_000L)

        assertEquals(120, result.bpm)
    }
}
