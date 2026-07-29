package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.ui.RampController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TempoRampContractTest {

    @Test
    fun mt025_supportedChoicesMatchIos() {
        assertEquals(listOf(1, 2, 5, 10), RampController.supportedIncrements)
        assertEquals(listOf(4, 8, 16, 32, 48, 64), RampController.supportedIntervals)
    }

    @Test
    fun mt026_initialBeatEstablishesZeroAndEachIntervalAdvances() {
        RampController.supportedIntervals.forEach { interval ->
            val controller = RampController(enabled = true, increment = 2, interval = interval)

            assertNull("interval=$interval initial beat", controller.onBeat(100f))
            repeat(interval - 1) { assertNull("interval=$interval beat=$it", controller.onBeat(100f)) }
            assertEquals("interval=$interval step", 102f, controller.onBeat(100f))
        }
    }

    @Test
    fun mt026_resetRestartsTheInitialBeatRule() {
        val controller = RampController(enabled = true, increment = 5, interval = 4)
        repeat(5) { controller.onBeat(100f) }

        controller.reset()

        assertNull(controller.onBeat(100f))
        repeat(3) { assertNull(controller.onBeat(100f)) }
        assertEquals(105f, controller.onBeat(100f))
    }

    @Test
    fun mt027_subdivisionsDoNotEnterRampState() {
        val controller = RampController(enabled = true, increment = 5, interval = 4)
        val standardEventFlags = listOf(true, false, false, false, true, false, false, false)

        standardEventFlags.forEach { isBeat ->
            if (isBeat) assertNull(controller.onBeat(100f))
        }

        assertNull(controller.onBeat(100f))
        assertNull(controller.onBeat(100f))
        assertEquals(105f, controller.onBeat(100f))
    }

    @Test
    fun mt027_oddMeterAccentsAreRampBeatEvents() {
        val controller = RampController(enabled = true, increment = 5, interval = 4)
        val sevenEightAccents = listOf(true, false, false, true, false, true, false)
        val results = sevenEightAccents
            .filter { it }
            .map { controller.onBeat(100f) }

        assertEquals(listOf(null, null, null), results)
        assertNull(controller.onBeat(100f))
        assertEquals(105f, controller.onBeat(100f))
    }

    @Test
    fun mt026_incrementCapsAtMaximumTempo() {
        RampController.supportedIncrements.forEach { increment ->
            val controller = RampController(enabled = true, increment = increment, interval = 4)
            repeat(4) { controller.onBeat(MetronomeConstants.MAX_BPM - 1f) }

            assertEquals(MetronomeConstants.MAX_BPM, controller.onBeat(MetronomeConstants.MAX_BPM - 1f))
            repeat(4) { controller.onBeat(MetronomeConstants.MAX_BPM) }
            assertNull(controller.onBeat(MetronomeConstants.MAX_BPM))
        }
    }
}
