package com.bfunkstudios.beatclikr.services

import org.junit.Assert.assertEquals
import org.junit.Test

class EventDispatchPolicyTest {
    @Test
    fun onTimeOrSubIntervalLateDispatchDoesNotSkip() {
        assertEquals(0, expiredEventCount(90, 100, 10, 25))
        assertEquals(0, expiredEventCount(114, 100, 10, 25))
    }

    @Test
    fun wholeExpiredIntervalsAreDroppedWithoutEnumeratingThem() {
        assertEquals(1, expiredEventCount(115, 100, 10, 25))
        assertEquals(4_000_000, expiredEventCount(100_000_090, 100, 10, 25))
    }

    @Test
    fun timestampGapCountsDeviceSilenceBeyondPresentedFrames() {
        assertEquals(
            240,
            missingPresentationFrames(
                previousPresentedFrame = 1_000,
                previousPresentationNanoTime = 2_000_000_000,
                currentPresentedFrame = 1_240,
                currentPresentationNanoTime = 2_010_000_000,
                sampleRate = 48_000
            )
        )
    }
}
