package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PolyrhythmConfigurationTest {

    @Test
    fun mt015_mt017_allSupportedRatiosPreserveIosBeatsAndAgainstMeaning() {
        for (beats in PolyrhythmConfiguration.SUPPORTED_COUNT) {
            for (against in PolyrhythmConfiguration.SUPPORTED_COUNT) {
                val configuration = PolyrhythmConfiguration(
                    bpm = ExactTempo.parse("137.5"),
                    beats = beats,
                    against = against
                )

                assertEquals("$beats:$against rhythm count", beats, configuration.rhythmCount)
                assertEquals("$beats:$against beat count", against, configuration.beatCount)
            }
        }
    }

    @Test
    fun mt016_cycleAndStreamIntervalsUseExactQuarterNoteArithmetic() {
        val threeAgainstTwo = PolyrhythmConfiguration(
            bpm = ExactTempo.of(120),
            beats = 3,
            against = 2
        )

        assertEquals(ExactFraction.of(1), threeAgainstTwo.cycleDurationSeconds)
        assertEquals(ExactFraction.parseDecimal("0.5"), threeAgainstTwo.beatIntervalSeconds)
        assertEquals(
            ExactFraction.of(1) / ExactFraction.of(3),
            threeAgainstTwo.rhythmIntervalSeconds
        )
    }

    @Test
    fun mt016_allSupportedRatiosDeriveExactIntervalsWithoutFloatingPoint() {
        val bpm = ExactTempo.parse("137.5")
        for (beats in PolyrhythmConfiguration.SUPPORTED_COUNT) {
            for (against in PolyrhythmConfiguration.SUPPORTED_COUNT) {
                val configuration = PolyrhythmConfiguration(bpm, beats, against)
                val expectedCycle = ExactFraction.of(against.toLong()) * bpm.quarterNoteDurationSeconds

                assertEquals(expectedCycle, configuration.cycleDurationSeconds)
                assertEquals(bpm.quarterNoteDurationSeconds, configuration.beatIntervalSeconds)
                assertEquals(
                    expectedCycle / ExactFraction.of(beats.toLong()),
                    configuration.rhythmIntervalSeconds
                )
            }
        }
    }

    @Test
    fun mt017_valuesOutsideOneThroughFifteenFailAtTheModelBoundary() {
        listOf(0, 16).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                PolyrhythmConfiguration(ExactTempo.of(120), invalid, 2)
            }
            assertThrows(IllegalArgumentException::class.java) {
                PolyrhythmConfiguration(ExactTempo.of(120), 3, invalid)
            }
        }
    }

    @Test
    fun configurationCopyRetainsImmutableValueSemantics() {
        val original = PolyrhythmConfiguration(
            bpm = ExactTempo.of(120),
            beats = 3,
            against = 2
        )
        val mutedConfiguration = original.copy(muteMetronome = true)

        assertEquals(false, original.muteMetronome)
        assertEquals(true, mutedConfiguration.muteMetronome)
        assertEquals(original.bpm, mutedConfiguration.bpm)
        assertEquals(original.beats, mutedConfiguration.beats)
        assertEquals(original.against, mutedConfiguration.against)
    }
}
