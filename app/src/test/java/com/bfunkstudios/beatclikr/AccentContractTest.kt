package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.BeatPattern
import com.bfunkstudios.beatclikr.data.Groove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentContractTest {

    @Test
    fun mt005_mt006_androidOddMeterDefinitionsMatchIosFixtures() {
        assertEquals(listOf(1, 2), listOf(Groove.OddMeterQuarter.subdivisions, Groove.OddMeterEighth.subdivisions))
        assertEquals(BeatPattern.entries, AccentContractFixtures.oddMeterPatterns.map { it.pattern })

        AccentContractFixtures.oddMeterPatterns.forEach { fixture ->
            assertEquals(fixture.groups.joinToString(","), fixture.pattern.rawValue)
            assertEquals(fixture.accents, fixture.pattern.accentArray)
            assertTrue(fixture.accents.first())
            assertEquals(fixture.groups.size, fixture.accents.count { it })
        }
    }
}
