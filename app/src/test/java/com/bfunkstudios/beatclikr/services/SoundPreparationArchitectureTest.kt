package com.bfunkstudios.beatclikr.services

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Test

class SoundPreparationArchitectureTest {
    @Test
    fun renderLoopCannotDecodeLoadOrPrepareSounds() {
        val source = locateSource("AudioTrackEngine.kt").readText()
        val renderLoop = source.substringAfter("private val renderRunnable")
            .substringBefore("private fun drainPendingClicks")

        listOf("pcmFileCache", "prepareBank", "prepareSounds", "openRawResource").forEach {
            assertFalse("Render loop contains sound preparation token: $it", renderLoop.contains(it))
        }
        assertFalse(source.contains("ensureWaveform"))
    }

    private fun locateSource(name: String): Path {
        val relative = Path.of("src/main/java/com/bfunkstudios/beatclikr/services/$name")
        return generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull(Path::isRegularFile)
            ?: error("Cannot locate $name")
    }
}
