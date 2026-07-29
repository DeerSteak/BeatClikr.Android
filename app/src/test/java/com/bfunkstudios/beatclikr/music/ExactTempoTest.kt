package com.bfunkstudios.beatclikr.music

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExactTempoTest {

    @Test
    fun mt001_mt003_decimalTempoRetainsItsExactRationalValue() {
        val tempo = ExactTempo.parse("137.500")

        assertEquals(BigInteger.valueOf(275), tempo.beatsPerMinute.numerator)
        assertEquals(BigInteger.valueOf(2), tempo.beatsPerMinute.denominator)
        assertEquals(BigInteger.valueOf(24), tempo.quarterNoteDurationSeconds.numerator)
        assertEquals(BigInteger.valueOf(55), tempo.quarterNoteDurationSeconds.denominator)
    }

    @Test
    fun mt003_floatBoundaryConversionUsesItsDecimalRepresentationOnce() {
        assertEquals(ExactTempo.parse("137.5"), ExactTempo.fromFloat(137.5f))
        assertEquals(ExactTempo.of(30), ExactTempo.fromFloat(30f))
        assertEquals(ExactTempo.of(240), ExactTempo.fromFloat(240f))
    }

    @Test
    fun mt002_supportedBoundsAreInclusiveAndValuesOutsideFail() {
        assertEquals(ExactTempo.of(30), ExactTempo.minimum)
        assertEquals(ExactTempo.of(240), ExactTempo.maximum)
        assertThrows(IllegalArgumentException::class.java) { ExactTempo.parse("29.999") }
        assertThrows(IllegalArgumentException::class.java) { ExactTempo.parse("240.001") }
    }

    @Test
    fun tb003_framesPerQuarterRemainAnExactFraction() {
        val frames = ExactTempo.parse("137.5").framesPerQuarter(48_000)

        assertEquals(BigInteger.valueOf(230_400), frames.numerator)
        assertEquals(BigInteger.valueOf(11), frames.denominator)
    }

    @Test
    fun equivalentDecimalScalesNormalizeToEqualValues() {
        assertEquals(ExactTempo.parse("120"), ExactTempo.parse("120.0"))
        assertEquals(ExactTempo.parse("120.0"), ExactTempo.parse("120.000"))
        assertNotEquals(ExactTempo.parse("120"), ExactTempo.parse("120.001"))
    }

    @Test
    fun invalidSampleRatesFailBeforeTimelineConstruction() {
        val tempo = ExactTempo.of(120)

        assertThrows(IllegalArgumentException::class.java) { tempo.framesPerQuarter(0) }
        assertThrows(IllegalArgumentException::class.java) { tempo.framesPerQuarter(-1) }
    }

}
