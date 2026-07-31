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
    fun productionEnginePortExposesOnlySessionAwareTransportMutation() {
        val coordinator = locateMainSource("services/PlaybackCoordinator.kt").readText()
        val port = coordinator.substringAfter("interface PlaybackEnginePort {")
            .substringBefore("sealed interface PlaybackEngineUpdateResult")
        val service = locateMainSource("services/AudioPlayerService.kt").readText()

        assertFalse(coordinator.contains("interface PlaybackEnginePort : IAudioPlayerService"))
        listOf(
            "fun startMetronome(",
            "fun stopMetronome(",
            "fun updateTempo(",
            "fun startPolyrhythm(",
            "fun stopPolyrhythm("
        ).forEach { sessionless ->
            assertFalse("Engine port exposes $sessionless", port.contains(sessionless))
            assertFalse("Production adapter exposes $sessionless", service.contains(sessionless))
        }
        assertTrue(port.contains("fun beginStandardSession("))
        assertTrue(port.contains("fun stopSession(sessionId: PlaybackSessionId"))
    }

    @Test
    fun focusLossCanOnlyPublishSessionTaggedCoordinatorInput() {
        val engine = locateMainSource("services/MetronomeAudioEngine.kt").readText()
        val listener = engine.substringAfter("private val focusListener")
            .substringBefore("private val audioFocusRequest")

        assertTrue(listener.contains("activeCoordinatorSessionId"))
        assertTrue(listener.contains("playbackInterruptionObserver?.invoke("))
        assertTrue(listener.contains("PlaybackInterruptionReason.AudioFocusLost"))
        assertFalse(listener.contains("stopMetronome()"))
        assertFalse(listener.contains("stopPolyrhythm()"))
    }

    @Test
    fun viewModelCleanupCannotMutateTransport() {
        val metronome = locateMainSource("ui/MetronomeViewModel.kt").readText()
        val polyrhythm = locateMainSource("ui/PolyrhythmViewModel.kt").readText()

        listOf(metronome, polyrhythm).forEach { source ->
            val cleanup = source.substringAfter("override fun onCleared()")
                .substringBefore("\n    }")
            assertFalse(cleanup.contains("audio."))
        }
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

        assertTrue(source.contains("is PlaybackIntent.UpdateStandard -> {"))
        assertTrue(source.contains("is PlaybackIntent.UpdatePolyrhythm ->"))
        assertTrue(source.contains("is PlaybackIntent.SetMuted ->"))
        assertTrue(source.contains("engine.stopSession("))
        assertTrue(source.contains("pendingReplacement = start"))
        assertTrue(source.contains("amendTransportConfiguration"))
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

    @Test
    fun legacyOwnershipModeIsProjectedOnlyFromTransportTransitions() {
        val source = locateMainSource("services/PlaybackCoordinator.kt").readText()
        val transition = source.substringAfter("private fun transitionTo(")
            .substringBefore("private fun newSessionId")

        assertTrue(transition.contains("activeMode = (next as? PlaybackTransportState.Playing)"))
    }

    @Test
    fun playbackViewModelsProjectCoordinatorStateWithoutEngineDelegates() {
        val metronome = locateMainSource("ui/MetronomeViewModel.kt").readText()
        val polyrhythm = locateMainSource("ui/PolyrhythmViewModel.kt").readText()
        val module = locateMainSource("di/AppModule.kt").readText()

        listOf(metronome, polyrhythm).forEach { source ->
            assertTrue(source.contains("private val playback: PlaybackObservation"))
            assertTrue(source.contains("playback.transportState.collect"))
            assertTrue(source.contains("playback.committedEvents.collect"))
            assertFalse(source.contains("audio.delegate ="))
            assertFalse(source.contains("audio.polyrhythmDelegate ="))
            assertFalse(source.contains("recordMetronomePractice"))
            assertFalse(source.contains("recordPolyrhythmPractice"))
            assertFalse(source.contains("recordSongPlayed"))
        }
        assertTrue(module.contains("providePlaybackObservation"))
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
