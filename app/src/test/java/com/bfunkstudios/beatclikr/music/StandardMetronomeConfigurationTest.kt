package com.bfunkstudios.beatclikr.music

import com.bfunkstudios.beatclikr.data.BeatPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardMetronomeConfigurationTest {

    @Test
    fun mt004_regularSubdivisionMappingMatchesApprovedContract() {
        assertEquals(
            listOf(1, 2, 3, 4),
            StandardSubdivision.entries.map { it.subdivisions }
        )
    }

    @Test
    fun mt005_additiveStepUnitsMapQuarterAndEighthTiming() {
        assertEquals(1, AdditiveStepUnit.QUARTER.subdivisions)
        assertEquals(2, AdditiveStepUnit.EIGHTH.subdivisions)
    }

    @Test
    fun mt006_everyAndroidAdditivePatternFitsTheImmutableModel() {
        BeatPattern.entries.forEach { pattern ->
            val accents = AccentPattern.of(pattern.accentArray)

            assertEquals(pattern.accentArray, accents.toList())
            assertTrue(accents[0])
        }
    }

    @Test
    fun accentPatternDefensivelyCopiesItsInputAndOutput() {
        val source = mutableListOf(true, false, true)
        val pattern = AccentPattern.of(source)
        source[0] = false
        val exported = pattern.toList().toMutableList()
        exported[0] = false

        assertEquals(listOf(true, false, true), pattern.toList())
    }

    @Test
    fun invalidAccentPatternsFailAtTheModelBoundary() {
        assertThrows(IllegalArgumentException::class.java) { AccentPattern.of(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            AccentPattern.of(listOf(false, true))
        }
    }

    @Test
    fun configurationCopiesPreserveValueSemanticsWithoutMutation() {
        val original = StandardMetronomeConfiguration(
            tempo = ExactTempo.parse("137.5"),
            timing = StandardTiming.Regular(StandardSubdivision.SIXTEENTH),
            alternateSixteenth = true,
            muteMetronome = false
        )

        val mutedConfiguration = original.copy(muteMetronome = true)

        assertEquals(false, original.muteMetronome)
        assertEquals(true, mutedConfiguration.muteMetronome)
        assertEquals(original.tempo, mutedConfiguration.tempo)
        assertEquals(original.timing, mutedConfiguration.timing)
    }
}
