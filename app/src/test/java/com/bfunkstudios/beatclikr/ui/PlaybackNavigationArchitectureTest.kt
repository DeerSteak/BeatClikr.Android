package com.bfunkstudios.beatclikr.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNavigationArchitectureTest {

    @Test
    fun topLevelNavigationUsesOneGlobalPlaybackStop() {
        val navigation = locateMainSource("ui/BeatClikrNavigation.kt").readText()
        val screen = locateMainSource("ui/BeatClikrScreen.kt").readText()

        assertTrue(navigation.contains("stopPlayback()"))
        assertFalse(navigation.contains("metronomeViewModel.stop()"))
        assertFalse(navigation.contains("polyrhythmViewModel.stop()"))
        assertTrue(screen.contains("stopPlayback = metronomeViewModel::stop"))
    }

    private fun locateMainSource(relative: String): Path {
        val source = Path.of("src/main/java/com/bfunkstudios/beatclikr").resolve(relative)
        return generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .map { it.resolve(source) }
            .firstOrNull(Files::exists)
            ?: error("Cannot locate main source: $relative")
    }
}
