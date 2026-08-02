package com.bfunkstudios.beatclikr.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeDurationFormatterTest {
    @Test
    fun subsecondDurationRoundsDownDeterministically() {
        assertEquals(
            PracticeDurationDisplay.Seconds(30),
            PracticeDurationFormatter.components(30_999_999_999L)
        )
    }

    @Test
    fun durationUsesAtMostTwoLargestUnits() {
        assertEquals(
            PracticeDurationDisplay.MinutesSeconds(2, 3),
            PracticeDurationFormatter.components(123_900_000_000L)
        )
        assertEquals(
            PracticeDurationDisplay.HoursMinutes(2, 4),
            PracticeDurationFormatter.components(7_499_000_000_000L)
        )
    }

    @Test
    fun negativeDurationDisplaysAsZero() {
        assertEquals(
            PracticeDurationDisplay.Seconds(0),
            PracticeDurationFormatter.components(-1)
        )
    }
}
