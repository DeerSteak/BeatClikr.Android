package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.PolyrhythmGrid
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolyrhythmContractTest {

    @Test
    fun mt015_mt017_allSupportedRatiosMatchIndependentContractTimelines() {
        assertEquals(225, PolyrhythmContractFixtures.allRatios.size)
        PolyrhythmContractFixtures.allRatios.forEach { fixture ->
            val grid = PolyrhythmGrid.create(fixture.beats, fixture.against)
            val actual = (0 until grid.lcm).mapNotNull { stepIndex ->
                val step = grid.stepAt(stepIndex)
                if (!step.beatFired && !step.rhythmFired) return@mapNotNull null
                PolyrhythmContractEvent(
                    stepIndex = stepIndex,
                    beatFired = step.beatFired,
                    rhythmFired = step.rhythmFired,
                    beatIndex = step.beatIndex,
                    rhythmIndex = step.rhythmIndex
                )
            }

            assertEquals("${fixture.beats}:${fixture.against} grid", fixture.gridSize, grid.lcm)
            assertEquals("${fixture.beats}:${fixture.against} events", fixture.events, actual)
            assertEquals(fixture.against, actual.count { it.beatFired })
            assertEquals(fixture.beats, actual.count { it.rhythmFired })
        }
    }

    @Test
    fun mt012_mt018_everyRatioBeginsAtSharedOriginAndPreservesExactCoincidences() {
        PolyrhythmContractFixtures.allRatios.forEach { fixture ->
            val coincident = fixture.events.filter { it.beatFired && it.rhythmFired }

            assertEquals(
                "${fixture.beats}:${fixture.against} origin",
                PolyrhythmContractEvent(0, true, true, 0, 0),
                coincident.first()
            )
            assertEquals(
                "${fixture.beats}:${fixture.against} coincidence count",
                greatestCommonDivisor(fixture.beats, fixture.against),
                coincident.size
            )
        }
    }

    @Test
    fun mt015_mt016_iosRepresentativeDurationAndIntervalFixturesMatch() {
        val threeAgainstTwo = PolyrhythmContractFixture(beats = 3, against = 2)
        assertEquals(1_000_000_000.0, threeAgainstTwo.cycleDurationNanos(120.0), 0.001)
        assertEquals(500_000_000.0, threeAgainstTwo.cycleDurationNanos(120.0) / 2, 0.001)
        assertEquals(1_000_000_000.0 / 3, threeAgainstTwo.cycleDurationNanos(120.0) / 3, 0.001)

        val fourAgainstThree = PolyrhythmContractFixture(beats = 4, against = 3)
        assertEquals(3_000_000_000.0, fourAgainstThree.cycleDurationNanos(60.0), 0.001)
        assertTrue(abs(fourAgainstThree.cycleDurationNanos(60.0) / 4 - 750_000_000.0) < 0.001)
    }

    @Test
    fun mt017_outOfRangeValuesClampToSupportedEndpoints() {
        assertEquals(1, PolyrhythmGrid.create(0, 0).beats)
        assertEquals(1, PolyrhythmGrid.create(0, 0).against)
        assertEquals(15, PolyrhythmGrid.create(16, 16).beats)
        assertEquals(15, PolyrhythmGrid.create(16, 16).against)
    }

    private fun greatestCommonDivisor(first: Int, second: Int): Int {
        var left = first
        var right = second
        while (right != 0) {
            val remainder = left % right
            left = right
            right = remainder
        }
        return left
    }
}
