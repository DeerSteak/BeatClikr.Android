package com.bfunkstudios.beatclikr

import org.junit.Assert.assertEquals
import org.junit.Test

class PolyrhythmStressExpectationTest {

    @Test
    fun fiveAgainstSevenPopulationsAreDerivedIndependentlyForOneMinuteAndOneHour() {
        val fixture = PolyrhythmContractFixture(5, 7)

        assertPopulation(fixture, bpm = 120, minutes = 1, frames = 188, beats = 120, rhythms = 86)
        assertPopulation(fixture, bpm = 120, minutes = 60, frames = 11_314, beats = 7_200, rhythms = 5_143)
    }

    @Test
    fun fifteenAgainstFourteenPopulationsAreDerivedIndependentlyForOneMinuteAndOneHour() {
        val fixture = PolyrhythmContractFixture(15, 14)

        assertPopulation(fixture, bpm = 240, minutes = 1, frames = 480, beats = 240, rhythms = 258)
        assertPopulation(fixture, bpm = 240, minutes = 60, frames = 28_800, beats = 14_400, rhythms = 15_429)
    }

    private fun assertPopulation(
        fixture: PolyrhythmContractFixture,
        bpm: Int,
        minutes: Int,
        frames: Int,
        beats: Int,
        rhythms: Int
    ) {
        val events = fixture.eventsBefore(bpm, minutes)
        assertEquals(frames, events.size)
        assertEquals(beats, events.count { it.beatFired })
        assertEquals(rhythms, events.count { it.rhythmFired })
    }

}
