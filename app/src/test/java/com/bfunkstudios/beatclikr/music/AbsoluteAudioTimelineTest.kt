package com.bfunkstudios.beatclikr.music

import java.math.BigInteger
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsoluteAudioTimelineTest {

    @Test
    fun mt032_fractionalIntervalsUseIndependentlyRoundedAbsoluteFrames() {
        val timeline = AbsoluteAudioTimeline(
            sampleRate = 44_100,
            intervalsPerMinute = ExactFraction.of(137) * 3
        )

        listOf(0L, 1L, 2L, 7L, 14L, 1_000_000L).forEach { index ->
            assertEquals(independentExpectedFrame(index), timeline.framePosition(index))
        }
    }

    @Test
    fun mt032_halfFramesRoundAwayFromZeroLikeIos() {
        val timeline = AbsoluteAudioTimeline(
            sampleRate = 1,
            intervalsPerMinute = ExactFraction.of(24)
        )

        assertEquals(0L, timeline.framePosition(0))
        assertEquals(3L, timeline.framePosition(1))
        assertEquals(5L, timeline.framePosition(2))
    }

    @Test
    fun tb001_twelveHoursAtAwkwardTempoRemainWithinHalfAFrame() {
        val timeline = AbsoluteAudioTimeline(
            sampleRate = 48_000,
            intervalsPerMinute = ExactFraction.parseDecimal("137.5") * 4
        )
        val intervalCount = 12L * 60 * 550
        val actual = timeline.framePosition(intervalCount)
        val exact = timeline.framesPerInterval * intervalCount
        val errorNumerator =
            BigInteger.valueOf(actual) * exact.denominator - exact.numerator

        assertTrue(errorNumerator.abs() * BigInteger.valueOf(2) <= exact.denominator)
    }

    @Test
    fun firstIntervalSearchReturnsTheFirstFrameAtOrAfterEveryQuery() {
        val timeline = AbsoluteAudioTimeline(
            sampleRate = 48_000,
            intervalsPerMinute = ExactFraction.parseDecimal("412.5")
        )
        val random = Random(23)

        repeat(10_000) {
            val frame = random.nextLong(0, 48_000L * 60 * 60)
            val index = timeline.firstIntervalAtOrAfter(frame)

            assertTrue(timeline.framePosition(index) >= frame)
            if (index > 0) assertTrue(timeline.framePosition(index - 1) < frame)
        }
    }

    @Test
    fun invalidTimelineInputsFailAtTheBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            AbsoluteAudioTimeline(0, ExactFraction.of(120))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AbsoluteAudioTimeline(48_000, ExactFraction.of(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AbsoluteAudioTimeline(1, ExactFraction.of(120))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AbsoluteAudioTimeline(48_000, ExactFraction.of(120)).framePosition(-1)
        }
    }

    private fun independentExpectedFrame(index: Long): Long {
        val numerator = BigInteger.valueOf(index) * BigInteger.valueOf(44_100L * 60)
        val denominator = BigInteger.valueOf(137L * 3)
        val division = numerator.divideAndRemainder(denominator)
        val rounded = division[0] +
            if (division[1] * BigInteger.valueOf(2) >= denominator) BigInteger.ONE else BigInteger.ZERO
        return rounded.longValueExact()
    }
}
