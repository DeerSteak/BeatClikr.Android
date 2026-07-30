package com.bfunkstudios.beatclikr.services

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameProductionSelectionArchitectureTest {
    @Test
    fun productionSelectsFrameAudioBeforeLegacyFallback() {
        val source = locateSource("MetronomeAudioEngine.kt").readText()

        assertTrue(source.contains("frameAudioActive = engine.startStandard("))
        assertTrue(source.contains("frameAudioActive = engine.startPolyrhythm("))
        assertTrue(source.contains("if (!isMuted && !frameAudioActive)"))
        assertTrue(source.contains("if (!frameAudioActive) audioTrackEngine?.playBeat()"))
        assertTrue(source.contains("if (!requestAudioFocus()) return@post"))
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
    fun activePolyrhythmUpdatesRetuneBeforeAnyFallbackCanOpen() {
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

    private fun locateSource(name: String): Path {
        val relative = Path.of("src/main/java/com/bfunkstudios/beatclikr/services/$name")
        return generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull(Path::isRegularFile)
            ?: error("Cannot locate $name")
    }
}
