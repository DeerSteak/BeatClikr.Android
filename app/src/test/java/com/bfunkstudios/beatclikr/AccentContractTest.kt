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
        assertEquals(BeatPattern.entries, UnitAccentFixtures.patterns.map { it.pattern })

        UnitAccentFixtures.patterns.forEach { fixture ->
            assertEquals(fixture.groups.joinToString(","), fixture.pattern.rawValue)
            assertEquals(fixture.accents, fixture.pattern.accentArray)
            assertTrue(fixture.accents.first())
            assertEquals(fixture.groups.size, fixture.accents.count { it })
        }
    }
}

private data class UnitOddMeterFixture(
    val pattern: BeatPattern,
    val groups: List<Int>
) {
    val accents: List<Boolean>
        get() = groups.flatMap { group -> listOf(true) + List(group - 1) { false } }
}

private object UnitAccentFixtures {
    val patterns = listOf(
        UnitOddMeterFixture(BeatPattern.FiveEightA, listOf(3, 2)),
        UnitOddMeterFixture(BeatPattern.FiveEightB, listOf(2, 3)),
        UnitOddMeterFixture(BeatPattern.SevenEightA, listOf(3, 2, 2)),
        UnitOddMeterFixture(BeatPattern.SevenEightB, listOf(2, 2, 3)),
        UnitOddMeterFixture(BeatPattern.SevenEightC, listOf(2, 3, 2)),
        UnitOddMeterFixture(BeatPattern.NineEightA, listOf(2, 2, 2, 3)),
        UnitOddMeterFixture(BeatPattern.NineEightB, listOf(3, 3, 3)),
        UnitOddMeterFixture(BeatPattern.ElevenEightA, listOf(2, 2, 3, 2, 2)),
        UnitOddMeterFixture(BeatPattern.ElevenEightB, listOf(3, 3, 2, 3)),
        UnitOddMeterFixture(BeatPattern.ThirteenEightA, listOf(3, 2, 2, 3, 3)),
        UnitOddMeterFixture(BeatPattern.ThirteenEightB, listOf(2, 3, 2, 3, 3)),
        UnitOddMeterFixture(BeatPattern.FifteenEightA, listOf(3, 3, 3, 3, 3)),
        UnitOddMeterFixture(BeatPattern.FifteenEightB, listOf(2, 3, 2, 3, 2, 3))
    )
}
