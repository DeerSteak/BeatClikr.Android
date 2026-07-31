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
    fun taggedStaleSoundFailureCannotOverwriteNewerRequest() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            val staleSequence = coordinator.submit(
                PlaybackIntent.SelectSoundBank(SoundBank.SYNTH)
            )
            coordinator.submit(PlaybackIntent.SelectSoundBank(SoundBank.ACOUSTIC))
            assertTrue(coordinator.awaitControlIdle())
            val failure = SoundPreparationFailure(
                SoundBank.SYNTH,
                SoundFile.CLICK_HI,
                SoundPreparationFailureCode.CORRUPT
            )

            engine.publish(engine.activeSounds, failure, staleSequence)
            assertTrue(coordinator.awaitControlIdle())

            assertNull(coordinator.ownership.value.soundPreparationFailure)
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

    @Test
    fun startPublishesAuthoritativeLifecycleInOrder() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(
                listOf("Preparing", "Preparing", "Starting", "Playing"),
                coordinator.stateTransitions.replayCache.map {
                    it.to::class.simpleName
                }
            )
            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            assertEquals(PlaybackMode.STANDARD, playing.context.mode)
            assertEquals(AudioBackendType.AUDIO_TRACK, playing.context.backend)
            val committed = coordinator.committedEvents.replayCache
            val scheduledIndex = committed.indexOfFirst {
                it is PlaybackCommittedEvent.FirstEventScheduled
            }
            val playingIndex = committed.indexOfFirst {
                it is PlaybackCommittedEvent.StateChanged &&
                    it.transition.to is PlaybackTransportState.Playing
            }
            assertTrue(scheduledIndex >= 0)
            assertTrue(scheduledIndex < playingIndex)
            assertEquals(
                3_216L,
                (committed[scheduledIndex] as PlaybackCommittedEvent.FirstEventScheduled)
                    .intendedFrame
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun inPlaceUpdateAmendsPlayingWithoutChangingSession() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val original = coordinator.transportState.value as PlaybackTransportState.Playing

            coordinator.submit(PlaybackIntent.UpdateStandard(121f, 2, null, true))
            coordinator.submit(PlaybackIntent.SetMuted(true))
            assertTrue(coordinator.awaitControlIdle())

            val amended = coordinator.transportState.value as PlaybackTransportState.Playing
            val configuration =
                amended.context.configuration as CommittedPlaybackConfiguration.Standard
            assertEquals(original.context.sessionId, amended.context.sessionId)
            assertEquals(121f, configuration.bpm)
            assertEquals(2, configuration.subdivisions)
            assertTrue(configuration.alternateSixteenth)
            assertTrue(configuration.muted)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun replacementAndRepeatedStopHaveNoIdleFlickerOrDuplicateTeardown() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val beforeReplacement = coordinator.stateTransitions.replayCache.size

            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())
            val replacementStates = coordinator.stateTransitions.replayCache
                .drop(beforeReplacement)
                .map { it.to::class.simpleName }
            assertEquals(
                listOf("Stopping", "Preparing", "Preparing", "Starting", "Playing"),
                replacementStates
            )
            assertFalse(replacementStates.contains("Idle"))

            coordinator.submit(PlaybackIntent.Stop)
            coordinator.submit(PlaybackIntent.Stop)
            assertTrue(coordinator.awaitControlIdle())
            assertTrue(coordinator.transportState.value is PlaybackTransportState.Idle)
            assertEquals(2, engine.operations.count { it.startsWith("stop") })
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun staleEngineCallbacksCannotChangeCurrentSession() {
        val engine = FakePlaybackEngine().apply {
            autoStartAcknowledgement = false
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val first = (coordinator.transportState.value as PlaybackTransportState.Starting)
                .context.sessionId

            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())
            val second = (coordinator.transportState.value as PlaybackTransportState.Starting)
                .context.sessionId

            engine.publishStarted(first)
            engine.transportObserver?.engineStartFailed(first, "stale")
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(
                second,
                (coordinator.transportState.value as PlaybackTransportState.Starting)
                    .context.sessionId
            )

            engine.publishStarted(second)
            assertTrue(coordinator.awaitControlIdle())
            assertTrue(coordinator.transportState.value is PlaybackTransportState.Playing)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun modeReplacementBeforeFirstStartAcknowledgementStopsPendingSession() {
        val engine = FakePlaybackEngine().apply { autoStartAcknowledgement = false }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val first = (coordinator.transportState.value as PlaybackTransportState.Starting)
                .context.sessionId

            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())
            val second = (coordinator.transportState.value as PlaybackTransportState.Starting)
                .context.sessionId

            assertEquals(listOf("startStandard", "stopStandard", "startPolyrhythm"), engine.operations)
            assertTrue(second.value > first.value)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun modeReplacementAfterCommittedEventRejectsStaleOldSessionWork() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val first = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            engine.renderedBatch = renderedBatch(first, 31)
            coordinator.metronomeBeatFired(true, 0.5f, 0)
            assertTrue(coordinator.awaitControlIdle())

            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())
            val second = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            engine.renderedBatch = renderedBatch(first, 32)
            coordinator.metronomeBeatFired(true, 0.5f, 0)
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(second, (coordinator.transportState.value as PlaybackTransportState.Playing).context.sessionId)
            assertEquals(
                listOf(31L),
                coordinator.committedEvents.replayCache
                    .filterIsInstance<PlaybackCommittedEvent.Rendered>()
                    .map { it.eventSequence }
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun configurationAndSoundCommandsQueuedBehindStopCannotReviveSession() {
        val engine = FakePlaybackEngine().apply { blockStop = true }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            coordinator.submit(PlaybackIntent.Stop)
            assertTrue(engine.stopEntered.await(2, TimeUnit.SECONDS))

            val updateSequence = coordinator.submit(
                PlaybackIntent.UpdateStandard(140f, 2, null, false)
            )
            coordinator.submit(PlaybackIntent.SelectSounds(SoundFile.SNARE, SoundFile.COWBELL))
            engine.allowStop.countDown()
            assertTrue(coordinator.awaitControlIdle())

            assertTrue(coordinator.transportState.value is PlaybackTransportState.Idle)
            assertEquals(1, engine.operations.count { it == "startStandard" })
            assertFalse(engine.operations.contains("updateStandard"))
            assertTrue(engine.operations.contains("selectSounds"))
            val updateOutcome = coordinator.controlEvents.replayCache
                .filterIsInstance<PlaybackControlEvent.IntentCompleted>()
                .single { it.commandSequence == updateSequence }
                .outcome
            assertEquals(
                PlaybackCoordinatorFailureCode.MODE_MISMATCH,
                (updateOutcome as PlaybackIntentOutcome.Rejected).failure.code
            )
        } finally {
            engine.allowStop.countDown()
            coordinator.release()
        }
    }

    @Test
    fun routeRemovalAndEngineFailureDuringStartStopExactlyOnce() {
        listOf<(PlaybackSessionId) -> PlaybackSystemInput>(
            { sessionId ->
                PlaybackSystemInput.Interrupted(
                    sessionId,
                    PlaybackInterruptionReason.RouteLost
                )
            },
            { sessionId -> PlaybackSystemInput.EngineFailed(sessionId, "start failed") }
        ).forEach { createInput ->
            val engine = FakePlaybackEngine().apply {
                autoStartAcknowledgement = false
                autoStopAcknowledgement = false
            }
            val coordinator = PlaybackCoordinator(engine)
            try {
                coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
                assertTrue(coordinator.awaitControlIdle())
                val starting = coordinator.transportState.value as PlaybackTransportState.Starting

                coordinator.submitSystemInput(createInput(starting.context.sessionId))
                assertTrue(coordinator.awaitControlIdle())

                assertTrue(coordinator.transportState.value is PlaybackTransportState.Failed)
                assertEquals(1, engine.operations.count { it == "stopStandard" })
            } finally {
                coordinator.release()
            }
        }
    }

    @Test
    fun unavailablePrerequisiteFailsBeforeEngineStart() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submitSystemInput(
                PlaybackSystemInput.PrerequisitesChanged(
                    PlaybackPrerequisites(
                        audioFocusReady = false,
                        routeReady = true
                    )
                )
            )
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())

            val failed = coordinator.transportState.value as PlaybackTransportState.Failed
            val reason =
                failed.reason as PlaybackFailureReason.PrerequisiteUnavailable
            assertTrue(reason.missing.contains(PlaybackPrerequisite.AUDIO_FOCUS))
            assertFalse(engine.operations.contains("startStandard"))
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun interruptionStopsOnceAndNeverAutomaticallyResumes() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            val before = coordinator.stateTransitions.replayCache.size

            coordinator.submitSystemInput(
                PlaybackSystemInput.Interrupted(
                    sessionId,
                    PlaybackInterruptionReason.AudioFocusLost
                )
            )
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(
                listOf("Interrupted", "Idle"),
                coordinator.stateTransitions.replayCache
                    .drop(before)
                    .map { it.to::class.simpleName }
            )
            assertEquals(1, engine.operations.count { it == "stopStandard" })
            assertEquals(1, engine.operations.count { it == "startStandard" })
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun activeRouteChangeRecordsTypedReasonAndRequiresRestart() {
        val engine = FakePlaybackEngine().apply {
            autoStopAcknowledgement = false
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            val reason = PlaybackInterruptionReason.RouteChanged(
                AudioOutputRoute.BUILT_IN,
                AudioOutputRoute.BLUETOOTH
            )

            coordinator.submitSystemInput(PlaybackSystemInput.Interrupted(sessionId, reason))
            assertTrue(coordinator.awaitControlIdle())

            val interrupted =
                coordinator.transportState.value as PlaybackTransportState.Interrupted
            assertEquals(reason, interrupted.reason)
            assertEquals(1, engine.operations.count { it == "stopStandard" })
            assertEquals(1, engine.operations.count { it == "startStandard" })
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun activeMediaServerFailureFailsAndStopsSession() {
        val engine = FakePlaybackEngine().apply {
            autoStopAcknowledgement = false
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId

            coordinator.submitSystemInput(
                PlaybackSystemInput.EngineFailed(sessionId, "RENDER: DEVICE_DISCONNECTED")
            )
            assertTrue(coordinator.awaitControlIdle())

            val failed = coordinator.transportState.value as PlaybackTransportState.Failed
            assertEquals(
                PlaybackFailureReason.Engine("RENDER: DEVICE_DISCONNECTED"),
                failed.reason
            )
            assertEquals(1, engine.operations.count { it == "stopStandard" })
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun staleInterruptionAndEngineFailureInputsAreIgnored() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val current =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            val stale = PlaybackSessionId(current.value + 10)

            coordinator.submitSystemInput(
                PlaybackSystemInput.Interrupted(
                    stale,
                    PlaybackInterruptionReason.RouteLost
                )
            )
            coordinator.submitSystemInput(
                PlaybackSystemInput.EngineFailed(stale, "stale")
            )
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(
                current,
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            )
            assertFalse(engine.operations.contains("stopStandard"))
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun staleSessionGuardStillRecordsLatestPrerequisiteState() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val ended =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            coordinator.submitSystemInput(
                PlaybackSystemInput.PrerequisitesChanged(
                    PlaybackPrerequisites(
                        audioFocusReady = false,
                        routeReady = true
                    ),
                    ended
                )
            )
            assertTrue(coordinator.awaitControlIdle())
            assertTrue(coordinator.transportState.value is PlaybackTransportState.Idle)

            coordinator.submitSystemInput(
                PlaybackSystemInput.PrerequisitesChanged(
                    PlaybackPrerequisites.READY,
                    ended
                )
            )
            coordinator.submit(PlaybackIntent.StartStandard(121f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())

            assertTrue(coordinator.transportState.value is PlaybackTransportState.Playing)
            assertEquals(2, engine.operations.count { it == "startStandard" })
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun asynchronousControlExceptionIsTypedAndExecutorRemainsUsable() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            engine.throwOnStop = true

            coordinator.submitSystemInput(
                PlaybackSystemInput.Interrupted(
                    sessionId,
                    PlaybackInterruptionReason.RouteLost
                )
            )
            assertTrue(coordinator.awaitControlIdle())
            assertTrue(
                coordinator.controlEvents.replayCache.any {
                    it is PlaybackControlEvent.UnexpectedFailure
                }
            )

            engine.throwOnStop = false
            coordinator.submit(PlaybackIntent.Stop)
            assertTrue(coordinator.awaitControlIdle())
            assertTrue(coordinator.transportState.value is PlaybackTransportState.Idle)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun rendererRecordsPublishWithFrameCorrelatedPresentationTime() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            engine.renderedBatch = FrameAudioRenderedEventBatch(
                RenderedEventBatch(
                    listOf(
                        RenderedFrameEvent(
                            0,
                            sessionId.value,
                            12,
                            com.bfunkstudios.beatclikr.music.MusicalEventRole.STANDARD,
                            48_480,
                            false
                        )
                    ),
                    nextCaptureSequence = 1,
                    droppedRecords = 0
                ),
                sampleRate = 48_000,
                correlation = AudioFrameCorrelation(
                    writtenFrame = 48_000,
                    presentedFrame = 48_000,
                    presentationNanoTime = 2_000_000_000
                )
            )

            coordinator.metronomeBeatFired(true, 0.5f, 2_010_000_000)
            assertTrue(coordinator.awaitControlIdle())

            val rendered = coordinator.committedEvents.replayCache
                .filterIsInstance<PlaybackCommittedEvent.Rendered>()
                .single()
            assertEquals(12L, rendered.eventSequence)
            assertEquals(48_480L, rendered.intendedFrame)
            assertEquals(
                2_010_000_000L,
                (rendered.presentation as EventPresentation.Correlated)
                    .presentationNanoTime
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun slowTempoPlayingDoesNotWaitForFirstRenderedRecord() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(30f, 1, null, false))
            assertTrue(coordinator.awaitControlIdle())

            assertTrue(coordinator.transportState.value is PlaybackTransportState.Playing)
            assertTrue(
                coordinator.committedEvents.replayCache.none {
                    it is PlaybackCommittedEvent.Rendered
                }
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun renderedRecordExplicitlyReportsUnavailablePresentationCorrelation() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            engine.renderedBatch = FrameAudioRenderedEventBatch(
                RenderedEventBatch(
                    listOf(
                        RenderedFrameEvent(
                            0,
                            sessionId.value,
                            0,
                            com.bfunkstudios.beatclikr.music.MusicalEventRole.STANDARD,
                            3_216,
                            false
                        )
                    ),
                    1,
                    0
                ),
                48_000,
                null
            )

            coordinator.metronomeBeatFired(true, 0.5f, 0)
            assertTrue(coordinator.awaitControlIdle())

            assertTrue(
                coordinator.committedEvents.replayCache
                    .filterIsInstance<PlaybackCommittedEvent.Rendered>()
                    .single()
                    .presentation is EventPresentation.Unavailable
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun renderedRecordsDrainWithoutLegacyTimingCallbacks() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            engine.renderedBatch = renderedBatch(sessionId, 17)

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (System.nanoTime() < deadline &&
                coordinator.committedEvents.replayCache.none {
                    it is PlaybackCommittedEvent.Rendered && it.eventSequence == 17L
                }) {
                Thread.sleep(5)
            }

            assertTrue(
                coordinator.committedEvents.replayCache.any {
                    it is PlaybackCommittedEvent.Rendered && it.eventSequence == 17L
                }
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun stopAcknowledgementDrainsFinalRenderedBlock() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId =
                (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
            engine.renderedBatchOnStop = renderedBatch(sessionId, 23)

            coordinator.submit(PlaybackIntent.Stop)
            assertTrue(coordinator.awaitControlIdle())

            assertTrue(
                coordinator.committedEvents.replayCache.any {
                    it is PlaybackCommittedEvent.Rendered && it.eventSequence == 23L
                }
            )
            assertTrue(coordinator.transportState.value is PlaybackTransportState.Idle)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun releaseFromInterruptedConvergesToIdleWithoutStopAcknowledgement() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
        assertTrue(coordinator.awaitControlIdle())
        val sessionId =
            (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
        engine.autoStopAcknowledgement = false
        coordinator.submitSystemInput(
            PlaybackSystemInput.Interrupted(
                sessionId,
                PlaybackInterruptionReason.AudioFocusLost
            )
        )
        assertTrue(coordinator.awaitControlIdle())

        coordinator.release()

        assertTrue(coordinator.awaitControlIdle())
        assertTrue(coordinator.transportState.value is PlaybackTransportState.Idle)
    }

    @Test
    fun audioFocusDenialPublishesTypedStartFailureAndStopsSession() {
        val engine = FakePlaybackEngine().apply {
            autoStartAcknowledgement = false
            autoStopAcknowledgement = false
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val starting = coordinator.transportState.value as PlaybackTransportState.Starting

            engine.transportObserver?.audioFocusUnavailable(starting.context.sessionId)
            assertTrue(coordinator.awaitControlIdle())

            val failed = coordinator.transportState.value as PlaybackTransportState.Failed
            assertEquals(PlaybackFailureReason.AudioFocusUnavailable, failed.reason)
            assertTrue(engine.operations.contains("stopStandard"))
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun audioFocusLossDuringStartingFailsAndStopsSession() {
        val engine = FakePlaybackEngine().apply {
            autoStartAcknowledgement = false
            autoStopAcknowledgement = false
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val starting = coordinator.transportState.value as PlaybackTransportState.Starting

            engine.transportObserver?.engineInterrupted(
                starting.context.sessionId,
                PlaybackInterruptionReason.AudioFocusLost
            )
            assertTrue(coordinator.awaitControlIdle())

            val failed = coordinator.transportState.value as PlaybackTransportState.Failed
            assertEquals(PlaybackFailureReason.AudioFocusUnavailable, failed.reason)
            assertTrue(engine.operations.contains("stopStandard"))
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun systemInputAfterReleasePublishesRejection() {
        val coordinator = PlaybackCoordinator(FakePlaybackEngine())
        coordinator.release()
        assertTrue(coordinator.awaitControlIdle())

        val sequence = coordinator.submitSystemInput(
            PlaybackSystemInput.PrerequisitesChanged(
                PlaybackPrerequisites.READY
            )
        )

        assertTrue(
            coordinator.controlEvents.replayCache.any {
                it is PlaybackControlEvent.SystemInputRejected &&
                    it.commandSequence == sequence &&
                    it.failure.code == PlaybackCoordinatorFailureCode.RELEASED
            }
        )
    }

    private fun renderedBatch(
        sessionId: PlaybackSessionId,
        eventSequence: Long
    ): FrameAudioRenderedEventBatch = FrameAudioRenderedEventBatch(
        RenderedEventBatch(
            listOf(
                RenderedFrameEvent(
                    0,
                    sessionId.value,
                    eventSequence,
                    com.bfunkstudios.beatclikr.music.MusicalEventRole.STANDARD,
                    3_216,
                    false
                )
            ),
            1,
            0
        ),
        48_000,
        null
    )

    private class FakePlaybackEngine : PlaybackEnginePort {
        val operations = Collections.synchronizedList(mutableListOf<String>())
        val callingThreads = Collections.synchronizedSet(mutableSetOf<String>())
        val maximumConcurrentCalls = AtomicInteger()
        private val activeCalls = AtomicInteger()
        var throwOnStart = false
        var throwOnStop = false
        var blockStart = false
        var blockStop = false
        var autoStartAcknowledgement = true
        val startEntered = CountDownLatch(1)
        val allowStart = CountDownLatch(1)
        val stopEntered = CountDownLatch(1)
        val allowStop = CountDownLatch(1)
        var activeSounds: ActiveSoundConfiguration? = ActiveSoundConfiguration(
            SoundBank.ACOUSTIC,
            SoundFile.CLICK_HI,
            SoundFile.CLICK_LO
        )
        var preparationFailure: SoundPreparationFailure? = null
        var renderedBatch: FrameAudioRenderedEventBatch? = null
        var renderedBatchOnStop: FrameAudioRenderedEventBatch? = null
        var autoStopAcknowledgement = true

        override var soundPreparationObserver: ((SoundPreparationPublication) -> Unit)? = null
        override var transportObserver: PlaybackEngineTransportObserver? = null
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
        override fun drainRenderedEvents(
            afterCaptureSequence: Long
        ): FrameAudioRenderedEventBatch? =
            renderedBatch?.also { renderedBatch = null }
        override fun selectSounds(
            requestSequence: Long,
            beatResourceId: Int,
            rhythmResourceId: Int
        ) = setupAudioPlayer(beatResourceId, rhythmResourceId)

        override fun selectSoundBank(requestSequence: Long, bank: SoundBank) {
            soundBank = bank
        }

        override fun prepareSounds(
            requestSequence: Long,
            sounds: Collection<SoundFile>
        ) = prepareAudioTrackSounds(sounds)
        override fun beginStandardSession(
            sessionId: PlaybackSessionId,
            bpm: Float,
            subdivisions: Int,
            accentPattern: List<Boolean>?,
            alternateSixteenth: Boolean
        ) {
            startMetronome(bpm, subdivisions, accentPattern, alternateSixteenth)
            if (!throwOnStart && autoStartAcknowledgement) publishStarted(sessionId)
        }

        override fun beginPolyrhythmSession(
            sessionId: PlaybackSessionId,
            bpm: Float,
            beats: Int,
            against: Int
        ) {
            startPolyrhythm(bpm, beats, against)
            if (!throwOnStart && autoStartAcknowledgement) publishStarted(sessionId)
        }

        override fun stopSession(sessionId: PlaybackSessionId, mode: PlaybackMode) {
            if (throwOnStop) error("stop failed")
            if (blockStop) {
                stopEntered.countDown()
                allowStop.await()
            }
            when (mode) {
                PlaybackMode.STANDARD -> stopMetronome()
                PlaybackMode.POLYRHYTHM -> stopPolyrhythm()
                PlaybackMode.NONE -> Unit
            }
            renderedBatchOnStop?.let {
                renderedBatch = it
                renderedBatchOnStop = null
            }
            if (autoStopAcknowledgement) transportObserver?.engineStopped(sessionId)
        }
        override fun release() = call("release")

        fun publishStarted(sessionId: PlaybackSessionId) {
            transportObserver?.engineStarted(
                PlaybackEngineStartEvidence(
                    sessionId,
                    requireNotNull(activeSounds),
                    AudioOutputRoute.UNKNOWN,
                    AudioBackendType.AUDIO_TRACK,
                    3_216
                )
            )
        }

        fun publish(
            active: ActiveSoundConfiguration?,
            failure: SoundPreparationFailure?,
            requestSequence: Long? = null
        ) {
            activeSounds = active
            preparationFailure = failure
            soundPreparationObserver?.invoke(
                SoundPreparationPublication(requestSequence, active, failure)
            )
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
