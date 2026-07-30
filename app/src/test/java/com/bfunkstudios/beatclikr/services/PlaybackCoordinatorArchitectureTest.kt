package com.bfunkstudios.beatclikr.services

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorArchitectureTest {

    @Test
    fun productionBindingExposesCoordinatorInsteadOfConcreteEngineOwner() {
        val module = locateMainSource("di/AppModule.kt").readText()

        assertTrue(module.contains("providePlaybackCoordinator"))
        assertTrue(
            module.contains(
                "fun provideAudioPlayerService(coordinator: PlaybackCoordinator): " +
                    "IAudioPlayerService"
            )
        )
        assertFalse(module.contains("AudioPlayerService.getInstance"))
    }

    @Test
    fun uiAndApplicationCodeCannotConstructConcreteEngineOwner() {
        val mainRoot = locateMainSource("")
        val offenders = Files.walk(mainRoot).use { paths ->
            paths
                .filter(Path::isRegularFile)
                .filter { it.toString().endsWith(".kt") }
                .filter { it.fileName.toString() != "AppModule.kt" }
                .filter { it.fileName.toString() != "AudioPlayerService.kt" }
                .filter { it.readText().contains("AudioPlayerService(") }
                .toList()
        }

        assertTrue("Concrete audio owner constructed outside DI: $offenders", offenders.isEmpty())
    }

    @Test
    fun coordinatorSeparatesModeReplacementFromInPlaceUpdates() {
        val source = locateMainSource("services/PlaybackCoordinator.kt").readText()

        assertTrue(source.contains("is PlaybackIntent.UpdateStandard -> engine.updateTempo"))
        assertTrue(source.contains("is PlaybackIntent.UpdatePolyrhythm ->"))
        assertTrue(source.contains("is PlaybackIntent.SetMuted ->"))
        assertTrue(source.contains("engine.stopPolyrhythm()"))
        assertTrue(source.contains("engine.stopMetronome()"))
    }

    @Test
    fun engineFailureCallbacksAreCompilerRequiredAndForwarded() {
        val engine = locateMainSource("services/MetronomeAudioEngine.kt").readText()
        val service = locateMainSource("services/AudioPlayerService.kt").readText()
        val coordinator = locateMainSource("services/PlaybackCoordinator.kt").readText()

        assertTrue(engine.contains("fun metronomeStartFailed()\\n".replace("\\n", "\n")))
        assertTrue(engine.contains("fun polyrhythmStartFailed()\\n".replace("\\n", "\n")))
        assertFalse(engine.contains("fun metronomeStartFailed() {}"))
        assertFalse(engine.contains("fun polyrhythmStartFailed() {}"))
        assertTrue(service.contains("override fun metronomeStartFailed()"))
        assertTrue(coordinator.contains("override fun metronomeStartFailed()"))
        assertTrue(coordinator.contains("override fun polyrhythmStartFailed()"))
    }

    @Test
    fun timingTrafficCannotEvictControlOutcomes() {
        val source = locateMainSource("services/PlaybackCoordinator.kt").readText()

        assertTrue(source.contains("val timingEvents: SharedFlow<PlaybackTimingEvent>"))
        assertTrue(source.contains("val controlEvents: SharedFlow<PlaybackControlEvent>"))
        assertFalse(source.contains("SharedFlow<PlaybackCoordinatorEvent>"))
    }

    private fun locateMainSource(relative: String): Path {
        val source = Path.of(
            "src/main/java/com/bfunkstudios/beatclikr"
        ).resolve(relative)
        return generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .map { it.resolve(source) }
            .firstOrNull { Files.exists(it) }
            ?: error("Cannot locate main source: $relative")
    }
}
