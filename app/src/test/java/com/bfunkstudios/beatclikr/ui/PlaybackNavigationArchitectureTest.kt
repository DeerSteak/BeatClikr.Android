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

    @Test
    fun internalLibraryAndPlaylistNavigationDoesNotStopPlayback() {
        val screen = locateMainSource("ui/BeatClikrScreen.kt").readText()
        val internalNavigation = screen.substringAfter("NavHost(")
            .substringBefore("\n    if (useSidebar) {\n        Row")

        assertFalse(internalNavigation.contains("stopPlayback"))
        assertTrue(internalNavigation.contains("showSongDetail = true"))
        assertTrue(internalNavigation.contains("navController.navigate(\"playlist_detail/\$playlistId\")"))
        assertTrue(screen.contains("showSongPickerForPlaylist = true"))
        assertTrue(screen.contains("showFocusView = true"))
    }

    @Test
    fun compactModeSwitchStopsTheInterfaceBeingHidden() {
        val container = locateMainSource("ui/MetronomeContainerView.kt").readText()

        assertTrue(container.contains("MetronomeMode.Metronome -> polyrhythmViewModel.stop()"))
        assertTrue(container.contains("MetronomeMode.Polyrhythm -> metronomeViewModel.stop()"))
    }

    private fun locateMainSource(relative: String): Path {
        val source = Path.of("src/main/java/com/bfunkstudios/beatclikr").resolve(relative)
        return generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .map { it.resolve(source) }
            .firstOrNull(Files::exists)
            ?: error("Cannot locate main source: $relative")
    }
}
