package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.PolyrhythmGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolyrhythmGridTest {

    @Test
    fun `three against two starts with shared downbeat`() {
        val grid = PolyrhythmGrid.create(beats = 3, against = 2)

        val downbeat = grid.stepAt(0)

        assertTrue(downbeat.beatFired)
        assertTrue(downbeat.rhythmFired)
        assertEquals(0, downbeat.beatIndex)
        assertEquals(0, downbeat.rhythmIndex)
    }

    @Test
    fun `three against two produces expected timeline`() {
        val grid = PolyrhythmGrid.create(beats = 3, against = 2)

        val steps = (0 until grid.lcm).map { grid.stepAt(it) }

        assertEquals(6, grid.lcm)
        assertEquals(listOf(true, false, true, true, true, false), steps.map { it.beatFired || it.rhythmFired })
        assertEquals(listOf(true, false, false, true, false, false), steps.map { it.beatFired })
        assertEquals(listOf(true, false, true, false, true, false), steps.map { it.rhythmFired })
    }

    @Test
    fun `five against four computes indexes at each fired step`() {
        val grid = PolyrhythmGrid.create(beats = 5, against = 4)

        val beatIndexes = (0 until grid.lcm)
            .map { grid.stepAt(it) }
            .filter { it.beatFired }
            .map { it.beatIndex }
        val rhythmIndexes = (0 until grid.lcm)
            .map { grid.stepAt(it) }
            .filter { it.rhythmFired }
            .map { it.rhythmIndex }

        assertEquals(listOf(0, 1, 2, 3), beatIndexes)
        assertEquals(listOf(0, 1, 2, 3, 4), rhythmIndexes)
    }

    @Test
    fun `non firing steps are marked inactive`() {
        val grid = PolyrhythmGrid.create(beats = 5, against = 4)

        val step = grid.stepAt(1)

        assertFalse(step.beatFired)
        assertFalse(step.rhythmFired)
    }
}
