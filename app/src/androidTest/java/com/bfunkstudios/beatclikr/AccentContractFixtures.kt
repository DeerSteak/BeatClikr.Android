package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.BeatPattern

internal data class OddMeterPatternFixture(
    val pattern: BeatPattern,
    val groups: List<Int>
) {
    val accents: List<Boolean>
        get() = groups.flatMap { group -> listOf(true) + List(group - 1) { false } }

    val soundRoles: List<ContractSoundRole>
        get() = accents.map { accented ->
            if (accented) ContractSoundRole.BEAT else ContractSoundRole.RHYTHM
        }
}

internal object AccentContractFixtures {
    val oddMeterPatterns = listOf(
        OddMeterPatternFixture(BeatPattern.FiveEightA, listOf(3, 2)),
        OddMeterPatternFixture(BeatPattern.FiveEightB, listOf(2, 3)),
        OddMeterPatternFixture(BeatPattern.SevenEightA, listOf(3, 2, 2)),
        OddMeterPatternFixture(BeatPattern.SevenEightB, listOf(2, 2, 3)),
        OddMeterPatternFixture(BeatPattern.SevenEightC, listOf(2, 3, 2)),
        OddMeterPatternFixture(BeatPattern.NineEightA, listOf(2, 2, 2, 3)),
        OddMeterPatternFixture(BeatPattern.NineEightB, listOf(3, 3, 3)),
        OddMeterPatternFixture(BeatPattern.ElevenEightA, listOf(2, 2, 3, 2, 2)),
        OddMeterPatternFixture(BeatPattern.ElevenEightB, listOf(3, 3, 2, 3)),
        OddMeterPatternFixture(BeatPattern.ThirteenEightA, listOf(3, 2, 2, 3, 3)),
        OddMeterPatternFixture(BeatPattern.ThirteenEightB, listOf(2, 3, 2, 3, 3)),
        OddMeterPatternFixture(BeatPattern.FifteenEightA, listOf(3, 3, 3, 3, 3)),
        OddMeterPatternFixture(BeatPattern.FifteenEightB, listOf(2, 3, 2, 3, 2, 3))
    )

    val oddMeterSubdivisions = listOf(1, 2)

    val alternateSixteenthEvents = listOf(
        StandardMetronomeEventFixture(0, isBeat = true, ContractSoundRole.BEAT),
        StandardMetronomeEventFixture(1, isBeat = false, ContractSoundRole.RHYTHM),
        StandardMetronomeEventFixture(2, isBeat = false, ContractSoundRole.BEAT),
        StandardMetronomeEventFixture(3, isBeat = false, ContractSoundRole.RHYTHM)
    )
}
