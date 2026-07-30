package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorTest {

    @Test
    fun modeReplacementStopsOldModeBeforeStartingNewMode() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(
                listOf(
                    "stopPolyrhythm",
                    "startStandard",
                    "stopStandard",
                    "startPolyrhythm"
                ),
                engine.operations
            )
            assertEquals(PlaybackMode.POLYRHYTHM, coordinator.ownership.value.activeMode)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun configurationAndMuteChangesDoNotTearDownTheActiveMode() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            engine.operations.clear()

            coordinator.submit(PlaybackIntent.UpdateStandard(121f, 4, null, false))
            coordinator.submit(PlaybackIntent.SetMuted(true))
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(listOf("updateStandard"), engine.operations)
            assertEquals(PlaybackMode.STANDARD, coordinator.ownership.value.activeMode)
            assertTrue(coordinator.ownership.value.muted)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun repeatedPolyrhythmStartUsesInPlaceUpdateCompatibilityPath() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.startPolyrhythm(120f, 3, 2)
            assertTrue(coordinator.awaitControlIdle())
            engine.operations.clear()

            coordinator.startPolyrhythm(121f, 5, 3)
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(listOf("startPolyrhythm"), engine.operations)
            assertEquals(PlaybackMode.POLYRHYTHM, coordinator.ownership.value.activeMode)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun mismatchedUpdateIsRejectedBeforeItReachesTheEngine() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            val sequence = coordinator.submit(
                PlaybackIntent.UpdateStandard(121f, 4, null, false)
            )
            assertTrue(coordinator.awaitControlIdle())

            val outcome = coordinator.ownership.value.lastOutcome
            assertTrue(outcome is PlaybackIntentOutcome.Rejected)
            assertEquals(sequence, outcome?.commandSequence)
            assertEquals(
                PlaybackCoordinatorFailureCode.MODE_MISMATCH,
                (outcome as PlaybackIntentOutcome.Rejected).failure.code
            )
            assertFalse(engine.operations.contains("updateStandard"))
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun submitDoesNotWaitForABlockingEnginePort() {
        val engine = FakePlaybackEngine()
        engine.blockStart = true
        val coordinator = PlaybackCoordinator(engine)
        try {
            val sequence = coordinator.submit(
                PlaybackIntent.StartStandard(120f, 4, null, false)
            )

            assertTrue(sequence > 0)
            assertTrue(engine.startEntered.await(2, TimeUnit.SECONDS))
            assertNull(coordinator.ownership.value.lastOutcome)
            engine.allowStart.countDown()
            assertTrue(coordinator.awaitControlIdle())
            assertTrue(coordinator.ownership.value.lastOutcome is PlaybackIntentOutcome.Accepted)
        } finally {
            engine.allowStart.countDown()
            coordinator.release()
        }
    }

    @Test
    fun bothEngineStartFailuresReachLegacyObservers() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        var standardFailed = false
        var polyrhythmFailed = false
        coordinator.delegate = object : MetronomeAudioEngineDelegate {
            override fun metronomeBeatFired(
                isBeat: Boolean,
                beatInterval: Float,
                beatTimeNanos: Long
            ) = Unit

            override fun metronomeStartFailed() {
                standardFailed = true
            }
        }
        coordinator.polyrhythmDelegate = object : PolyrhythmAudioEngineDelegate {
            override fun polyrhythmBeatFired(
                beatFired: Boolean,
                rhythmFired: Boolean,
                beatIndex: Int,
                rhythmIndex: Int,
                stepTimeNanos: Long,
                beatDurationNanos: Long,
                rhythmDurationNanos: Long
            ) = Unit

            override fun polyrhythmStartFailed() {
                polyrhythmFailed = true
            }
        }
        try {
            coordinator.metronomeStartFailed()
            coordinator.polyrhythmStartFailed()

            assertTrue(standardFailed)
            assertTrue(polyrhythmFailed)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun concurrentIntentsUseOneControlContextAndOneActiveMode() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        val callers = Executors.newFixedThreadPool(12)
        val ready = CountDownLatch(12)
        val start = CountDownLatch(1)
        val sequences = Collections.synchronizedList(mutableListOf<Long>())
        try {
            repeat(12) { index ->
                callers.execute {
                    ready.countDown()
                    start.await()
                    sequences += coordinator.submit(
                        if (index % 2 == 0) {
                            PlaybackIntent.StartStandard(120f, 4, null, false)
                        } else {
                            PlaybackIntent.StartPolyrhythm(120f, 3, 2)
                        }
                    )
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            callers.shutdown()
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(12, sequences.distinct().size)
            assertEquals(1, engine.maximumConcurrentCalls.get())
            assertTrue(coordinator.ownership.value.activeMode != PlaybackMode.NONE)
            assertEquals(setOf("PlaybackCoordinatorControl"), engine.callingThreads)
        } finally {
            callers.shutdownNow()
            coordinator.release()
        }
    }

    @Test
    fun invalidInputAndEngineExceptionsBecomeTypedRejections() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            val invalidSequence = coordinator.submit(
                PlaybackIntent.StartStandard(Float.NaN, 4, null, false)
            )
            assertTrue(coordinator.awaitControlIdle())
            val invalid = coordinator.ownership.value.lastOutcome
            assertTrue(invalid is PlaybackIntentOutcome.Rejected)
            assertEquals(invalidSequence, invalid?.commandSequence)
            assertEquals(
                PlaybackCoordinatorFailureCode.INVALID_INPUT,
                (invalid as PlaybackIntentOutcome.Rejected).failure.code
            )
            assertFalse(engine.operations.contains("startStandard"))

            engine.throwOnStart = true
            val failedSequence = coordinator.submit(
                PlaybackIntent.StartPolyrhythm(120f, 3, 2)
            )
            assertTrue(coordinator.awaitControlIdle())
            val failed = coordinator.ownership.value.lastOutcome
            assertTrue(failed is PlaybackIntentOutcome.Rejected)
            assertEquals(failedSequence, failed?.commandSequence)
            assertEquals(
                PlaybackCoordinatorFailureCode.ENGINE_FAILURE,
                (failed as PlaybackIntentOutcome.Rejected).failure.code
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun requestedSoundsDoNotReplaceAudibleSnapshotUntilMatchingPublication() {
        val engine = FakePlaybackEngine()
        val original = ActiveSoundConfiguration(
            SoundBank.ACOUSTIC,
            SoundFile.CLICK_HI,
            SoundFile.CLICK_LO
        )
        engine.activeSounds = original
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.SelectSoundBank(SoundBank.SYNTH))
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(SoundBank.SYNTH, coordinator.ownership.value.requestedSounds.bank)
            assertSame(original, coordinator.ownership.value.audibleSounds)

            val synth = ActiveSoundConfiguration(
                SoundBank.SYNTH,
                SoundFile.CLICK_HI,
                SoundFile.CLICK_LO
            )
            engine.publish(synth, null)
            assertTrue(coordinator.awaitControlIdle())

            assertSame(synth, coordinator.ownership.value.audibleSounds)
            assertNull(coordinator.ownership.value.soundPreparationFailure)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun staleSoundCompletionCannotOverwriteNewerRequestedSelection() {
        val engine = FakePlaybackEngine()
        val original = ActiveSoundConfiguration(
            SoundBank.ACOUSTIC,
            SoundFile.CLICK_HI,
            SoundFile.CLICK_LO
        )
        engine.activeSounds = original
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(
                PlaybackIntent.SelectSounds(SoundFile.KICK, SoundFile.SNARE)
            )
            engine.publish(original, null)
            assertTrue(coordinator.awaitControlIdle())

            assertSame(original, coordinator.ownership.value.audibleSounds)
            assertEquals(
                SoundFile.KICK,
                coordinator.ownership.value.requestedSounds.beatSound
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun preparationFailurePreservesLastGoodAudibleSnapshot() {
        val engine = FakePlaybackEngine()
        val original = ActiveSoundConfiguration(
            SoundBank.ACOUSTIC,
            SoundFile.CLICK_HI,
            SoundFile.CLICK_LO
        )
        engine.activeSounds = original
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.SelectSoundBank(SoundBank.SYNTH))
            val failure = SoundPreparationFailure(
                SoundBank.SYNTH,
                SoundFile.CLICK_HI,
                SoundPreparationFailureCode.CORRUPT
            )
            engine.publish(original, failure)
            assertTrue(coordinator.awaitControlIdle())

            assertSame(original, coordinator.ownership.value.audibleSounds)
            assertSame(failure, coordinator.ownership.value.soundPreparationFailure)
        } finally {
            coordinator.release()
        }
    }

    private class FakePlaybackEngine : PlaybackEnginePort {
        val operations = Collections.synchronizedList(mutableListOf<String>())
        val callingThreads = Collections.synchronizedSet(mutableSetOf<String>())
        val maximumConcurrentCalls = AtomicInteger()
        private val activeCalls = AtomicInteger()
        var throwOnStart = false
        var blockStart = false
        val startEntered = CountDownLatch(1)
        val allowStart = CountDownLatch(1)
        var activeSounds: ActiveSoundConfiguration? = null
        var preparationFailure: SoundPreparationFailure? = null

        override var soundPreparationObserver:
            ((ActiveSoundConfiguration?, SoundPreparationFailure?) -> Unit)? = null
        override var delegate: MetronomeAudioEngineDelegate? = null
        override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate? = null
        override var isMuted: Boolean = false
        override var soundBank: SoundBank = SoundBank.ACOUSTIC

        override fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int) =
            call("selectSounds")

        override fun startMetronome(
            bpm: Float,
            subdivisions: Int,
            accentPattern: List<Boolean>?,
            alternateSixteenth: Boolean
        ) = call("startStandard") {
            if (throwOnStart) error("start failed")
            if (blockStart) {
                startEntered.countDown()
                allowStart.await()
            }
        }

        override fun stopMetronome() = call("stopStandard")

        override fun updateTempo(
            bpm: Float,
            subdivisions: Int,
            accentPattern: List<Boolean>?,
            alternateSixteenth: Boolean
        ) = call("updateStandard")

        override fun startPolyrhythm(bpm: Float, beats: Int, against: Int) =
            call("startPolyrhythm") {
                if (throwOnStart) error("start failed")
            }

        override fun stopPolyrhythm() = call("stopPolyrhythm")
        override fun prewarmAudioTrack() = call("prewarm")
        override fun prepareAudioTrackSounds(soundFiles: Collection<SoundFile>) =
            call("prepareSounds")
        override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? = null
        override fun activeSoundConfiguration(): ActiveSoundConfiguration? = activeSounds
        override fun soundPreparationFailure(): SoundPreparationFailure? = preparationFailure
        override fun release() = call("release")

        fun publish(
            active: ActiveSoundConfiguration?,
            failure: SoundPreparationFailure?
        ) {
            activeSounds = active
            preparationFailure = failure
            soundPreparationObserver?.invoke(active, failure)
        }

        private fun call(name: String, operation: () -> Unit = {}) {
            val concurrent = activeCalls.incrementAndGet()
            maximumConcurrentCalls.accumulateAndGet(concurrent, ::maxOf)
            callingThreads += Thread.currentThread().name
            try {
                operations += name
                operation()
            } finally {
                activeCalls.decrementAndGet()
            }
        }
    }
}
