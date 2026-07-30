package com.bfunkstudios.beatclikr.services

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameProductionSelectionArchitectureTest {
    @Test
    fun productionUsesOnlyFrameAudioForSoundOutput() {
        val engine = locateSource("MetronomeAudioEngine.kt").readText()
        val output = locateSource("FrameAudioEngine.kt").readText()

        assertTrue(engine.contains("frameAudioActive = engine.startStandard("))
        assertTrue(engine.contains("frameAudioActive = engine.startPolyrhythm("))
        assertTrue(engine.contains("if (!requestAudioFocus()) {"))
        listOf("pendingClicks", "enqueueWaveform", "fun playBeat(", "renderRunnable").forEach {
            assertFalse("Production output contains legacy token: $it", output.contains(it))
        }
    }

    @Test
    fun standardUpdatesDoNotStopOrRestartTheFrameStream() {
        val source = locateSource("MetronomeAudioEngine.kt").readText()
        val updateBody = source.substringAfter("fun updateTempo(").substringBefore("fun release()")

        assertTrue(updateBody.contains("engine.updateStandard("))
        assertFalse(updateBody.contains("engine.stop()"))
        assertFalse(updateBody.contains("engine.startStandard("))
    }

    @Test
    fun activePolyrhythmUpdatesRetuneBeforeAnyNewStart() {
        val source = locateSource("MetronomeAudioEngine.kt").readText()
        val startBody = source.substringAfter("fun startPolyrhythm(")
            .substringBefore("fun stopPolyrhythm()")

        assertTrue(startBody.contains("engine.updatePolyrhythm("))
        assertTrue(startBody.contains("return@post"))
        assertTrue(
            startBody.indexOf("engine.updatePolyrhythm(") <
                startBody.indexOf("engine.startPolyrhythm(")
        )
    }

    @Test
    fun coordinatorStopReleasesAHeldAudioFocusLease() {
        val source = locateSource("MetronomeAudioEngine.kt").readText()
        val stopBody = source.substringAfter("fun stopSession(").substringBefore("fun prewarm()")
        val releaseBody = source.substringAfter("fun release()")
            .substringBefore("private fun requestAudioFocus()")
        val abandonBody = source.substringAfter("private fun abandonAudioFocus()")
            .substringBefore("private fun doStart(")

        assertTrue(stopBody.contains("abandonAudioFocus()"))
        assertTrue(
            releaseBody.substringBefore("latch.countDown()").contains("abandonAudioFocus()")
        )
        assertFalse(
            source.substringAfter("private fun requestAudioFocus(): Boolean {")
                .substringBefore("val result")
                .contains("audioFocusHeld")
        )
        assertTrue(abandonBody.contains("if (!audioFocusHeld) return"))
        assertTrue(abandonBody.contains("audioFocusHeld = false"))
    }

    @Test
    fun visualCallbacksUseOneShotSchedulingInsteadOfPolling() {
        val standard = locateSource("MetronomeAudioEngine.kt").readText()
        val polyrhythm = locateSource("PolyrhythmTimingEngine.kt").readText()

        assertFalse(standard.contains("TIMER_CHECK_INTERVAL_MS"))
        assertFalse(standard.contains("checkAndPlayBeat"))
        assertFalse(polyrhythm.contains("checkIntervalMs"))
        assertFalse(polyrhythm.contains("checkAndPlayStep"))
        assertTrue(standard.contains("scheduleNextBeat()"))
        assertTrue(polyrhythm.contains("scheduleNextStep()"))
    }

    private fun locateSource(name: String): Path {
        val relative = Path.of("src/main/java/com/bfunkstudios/beatclikr/services/$name")
        return generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull(Path::isRegularFile)
            ?: error("Cannot locate $name")
    }
}
