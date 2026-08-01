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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorTest {

    @Test
    fun explicitStandardReplacementCreatesNewSessionAtRequestedConfiguration() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val first = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            engine.operations.clear()

            coordinator.submit(
                PlaybackIntent.ReplaceStandard(150f, 2, listOf(true, false), true)
            )
            assertTrue(coordinator.awaitControlIdle())

            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            val configuration = playing.context.configuration as
                CommittedPlaybackConfiguration.Standard
            assertTrue(playing.context.sessionId != first)
            assertEquals(listOf("stopStandard", "startStandard"), engine.operations)
            assertEquals(150f, configuration.bpm)
            assertEquals(2, configuration.subdivisions)
            assertEquals(listOf(true, false), configuration.accentPattern)
            assertTrue(configuration.alternateSixteenth)
            assertEquals(configuration, engine.standardStarts.last())
            assertTrue(
                coordinator.committedEvents.replayCache
                    .filterIsInstance<PlaybackCommittedEvent.FirstEventScheduled>()
                    .any { it.sessionId == playing.context.sessionId }
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun explicitSameModeReplacementHasNoIdleTransition() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val transitionStart = coordinator.stateTransitions.replayCache.size

            coordinator.submit(PlaybackIntent.ReplaceStandard(140f, 2, null, false))
            assertTrue(coordinator.awaitControlIdle())

            val replacementStates = coordinator.stateTransitions.replayCache
                .drop(transitionStart)
                .map { it.to }
            assertTrue(replacementStates.none { it is PlaybackTransportState.Idle })
            assertTrue(replacementStates.any { it is PlaybackTransportState.Stopping })
            assertTrue(replacementStates.any { it is PlaybackTransportState.Playing })
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun obsoleteOwnerStopCannotStopReplacementButGlobalStopCan() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val obsolete = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())
            val replacement = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            engine.operations.clear()

            coordinator.submit(PlaybackIntent.StopIfCurrent(obsolete))
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(replacement, (coordinator.transportState.value as PlaybackTransportState.Playing).context.sessionId)
            assertTrue(engine.operations.isEmpty())

            coordinator.submit(PlaybackIntent.Stop)
            assertTrue(coordinator.awaitControlIdle())
            assertTrue(coordinator.transportState.value is PlaybackTransportState.Idle)
            assertEquals(listOf("stopPolyrhythm"), engine.operations)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun obsoleteOwnerStopDuringReplacementCannotCancelPendingStart() {
        val engine = FakePlaybackEngine().apply { autoStopAcknowledgement = false }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val obsolete = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())
            coordinator.submit(PlaybackIntent.StopIfCurrent(obsolete))
            assertTrue(coordinator.awaitControlIdle())

            engine.transportObserver?.engineStopped(obsolete)
            assertTrue(coordinator.awaitControlIdle())
            assertTrue(coordinator.transportState.value is PlaybackTransportState.Playing)
            assertEquals(PlaybackMode.POLYRHYTHM, coordinator.ownership.value.activeMode)
        } finally {
            coordinator.release()
        }
    }

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
    fun repeatedPolyrhythmStartUsesAcknowledgedInPlaceUpdate() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.startPolyrhythm(120f, 3, 2)
            assertTrue(coordinator.awaitControlIdle())
            engine.operations.clear()

            coordinator.startPolyrhythm(121f, 5, 3)
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(listOf("updatePolyrhythm"), engine.operations)
            assertEquals(PlaybackMode.POLYRHYTHM, coordinator.ownership.value.activeMode)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun configurationWaitsForAsynchronousEngineAcknowledgement() {
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            coordinator.submit(PlaybackIntent.UpdateStandard(144f, 3, null, false))
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(120f, coordinator.standardConfiguration().bpm)
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(144f, coordinator.standardConfiguration().bpm)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun rendererRejectionDoesNotCommitAndLaterUpdateStillCompletes() {
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            coordinator.submit(PlaybackIntent.UpdateStandard(130f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            engine.completeNextUpdate(
                PlaybackEngineUpdateResult.Reason.RENDERER_REJECTED,
                "renderer rejected replacement"
            )
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(120f, coordinator.standardConfiguration().bpm)
            assertTrue(coordinator.ownership.value.lastOutcome is PlaybackIntentOutcome.Rejected)
            coordinator.submit(PlaybackIntent.UpdateStandard(140f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(140f, coordinator.standardConfiguration().bpm)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun synchronousPortExceptionBecomesTypedRejectionAndQueueContinues() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            engine.throwOnUpdate = true
            val rejected = coordinator.submit(
                PlaybackIntent.UpdateStandard(130f, 4, null, false)
            )
            assertTrue(coordinator.awaitControlIdle())
            assertRejectedOutcome(coordinator, rejected)

            engine.throwOnUpdate = false
            coordinator.submit(PlaybackIntent.UpdateStandard(140f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(140f, coordinator.standardConfiguration().bpm)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun lateUpdateAfterStopIsRejectedWithoutChangingTransport() {
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val update = coordinator.submit(PlaybackIntent.UpdateStandard(150f, 4, null, false))
            coordinator.submit(PlaybackIntent.Stop)
            assertTrue(coordinator.awaitControlIdle())
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())

            assertTrue(coordinator.transportState.value is PlaybackTransportState.Idle)
            val outcome = coordinator.controlEvents.replayCache
                .filterIsInstance<PlaybackControlEvent.IntentCompleted>()
                .first { it.commandSequence == update }
                .outcome
            assertTrue("Expected rejected update but was $outcome", outcome is PlaybackIntentOutcome.Rejected)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun sameModeStartDuringStartingUpdatesOnlyAfterStartEvidence() {
        val engine = FakePlaybackEngine().apply {
            autoStartAcknowledgement = false
            asynchronousUpdates = true
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId = (coordinator.transportState.value as PlaybackTransportState.Starting)
                .context.sessionId
            coordinator.submit(PlaybackIntent.StartStandard(160f, 2, null, false))
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(listOf("startStandard"), engine.operations)

            engine.publishStarted(sessionId)
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(120f, coordinator.standardConfiguration().bpm)
            assertEquals(listOf("startStandard", "updateStandard"), engine.operations)
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(160f, coordinator.standardConfiguration().bpm)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun sameModeDragDuringStartingCoalescesToNewestConfiguration() {
        val engine = FakePlaybackEngine().apply {
            autoStartAcknowledgement = false
            asynchronousUpdates = true
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId = (coordinator.transportState.value as PlaybackTransportState.Starting)
                .context.sessionId
            val first = coordinator.submit(
                PlaybackIntent.UpdateStandard(130f, 4, null, false)
            )
            val second = coordinator.submit(
                PlaybackIntent.UpdateStandard(140f, 4, null, false)
            )
            val latest = coordinator.submit(
                PlaybackIntent.UpdateStandard(150f, 4, null, false)
            )
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(listOf("startStandard"), engine.operations)
            assertFailureCode(coordinator, first, PlaybackCoordinatorFailureCode.SUPERSEDED)
            assertFailureCode(coordinator, second, PlaybackCoordinatorFailureCode.SUPERSEDED)

            engine.publishStarted(sessionId)
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(listOf("startStandard", "updateStandard"), engine.operations)
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(150f, coordinator.standardConfiguration().bpm)
            assertTrue(outcomeFor(coordinator, latest) is PlaybackIntentOutcome.Accepted)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun updateRejectionReasonsRemainStructuredCoordinatorOutcomes() {
        val mappings = listOf(
            PlaybackEngineUpdateResult.Reason.STALE_SESSION to
                PlaybackCoordinatorFailureCode.STALE_SESSION,
            PlaybackEngineUpdateResult.Reason.INACTIVE_MODE to
                PlaybackCoordinatorFailureCode.MODE_MISMATCH,
            PlaybackEngineUpdateResult.Reason.RENDERER_REJECTED to
                PlaybackCoordinatorFailureCode.RENDERER_REJECTED,
            PlaybackEngineUpdateResult.Reason.INVALID_CONFIGURATION to
                PlaybackCoordinatorFailureCode.INVALID_INPUT,
            PlaybackEngineUpdateResult.Reason.ENGINE_FAILURE to
                PlaybackCoordinatorFailureCode.ENGINE_FAILURE
        )
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            mappings.forEachIndexed { index, (reason, expectedCode) ->
                val sequence = coordinator.submit(
                    PlaybackIntent.UpdateStandard(130f + index, 4, null, false)
                )
                assertTrue(coordinator.awaitControlIdle())
                engine.completeNextUpdate(reason)
                assertTrue(coordinator.awaitControlIdle())
                assertFailureCode(coordinator, sequence, expectedCode)
            }
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun sameModeStartSubmittedDuringPreparingBecomesAcknowledgedLiveUpdate() {
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        try {
            engine.blockSoundSnapshot = true
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(engine.soundSnapshotEntered.await(1, TimeUnit.SECONDS))
            coordinator.submit(PlaybackIntent.StartStandard(170f, 2, null, false))
            engine.allowSoundSnapshot.countDown()
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(listOf("startStandard", "updateStandard"), engine.operations)
            assertEquals(120f, coordinator.standardConfiguration().bpm)
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(170f, coordinator.standardConfiguration().bpm)
        } finally {
            engine.allowSoundSnapshot.countDown()
            coordinator.release()
        }
    }

    @Test
    fun rapidUpdateStopStartPreservesPhysicalOperationOrderAndRejectsLateAck() {
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            coordinator.submit(PlaybackIntent.UpdateStandard(130f, 4, null, false))
            coordinator.submit(PlaybackIntent.Stop)
            coordinator.submit(PlaybackIntent.StartStandard(140f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(
                listOf("startStandard", "updateStandard", "stopStandard", "startStandard"),
                engine.operations
            )
            assertEquals(140f, coordinator.standardConfiguration().bpm)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun queuedUpdatesReachEngineOneAtATimeInCommandOrder() {
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            coordinator.submit(PlaybackIntent.UpdateStandard(130f, 4, null, false))
            coordinator.submit(PlaybackIntent.UpdateStandard(140f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(1, engine.operations.count { it == "updateStandard" })

            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(2, engine.operations.count { it == "updateStandard" })
            assertEquals(130f, coordinator.standardConfiguration().bpm)
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())
            assertEquals(140f, coordinator.standardConfiguration().bpm)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun updateCompletionAfterModeReplacementCannotAmendReplacement() {
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val update = coordinator.submit(PlaybackIntent.UpdateStandard(130f, 4, null, false))
            coordinator.submit(PlaybackIntent.StartPolyrhythm(140f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())

            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            assertEquals(PlaybackMode.POLYRHYTHM, playing.context.mode)
            assertRejectedOutcome(coordinator, update)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun asynchronousReplacementRejectsStaleStartEvidenceAtRealThreadBoundary() {
        val engine = FakePlaybackEngine().apply { autoStartAcknowledgement = false }
        val coordinator = PlaybackCoordinator(engine)
        val callbacks = Executors.newSingleThreadExecutor()
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val first = (coordinator.transportState.value as PlaybackTransportState.Starting)
                .context.sessionId

            coordinator.submit(PlaybackIntent.ReplaceStandard(140f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val replacement =
                (coordinator.transportState.value as PlaybackTransportState.Starting)
                    .context.sessionId
            callbacks.submit { engine.publishStarted(first) }.get(2, TimeUnit.SECONDS)
            callbacks.submit { engine.publishStarted(replacement) }.get(2, TimeUnit.SECONDS)
            assertTrue(coordinator.awaitControlIdle())

            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            assertEquals(replacement, playing.context.sessionId)
            assertEquals(2, engine.operations.count { it == "startStandard" })
            assertEquals(1, engine.operations.count { it == "stopStandard" })
            assertEquals(
                listOf(replacement),
                coordinator.committedEvents.replayCache
                    .filterIsInstance<PlaybackCommittedEvent.FirstEventScheduled>()
                    .map { it.sessionId }
            )
            assertEquals(
                listOf(1L),
                coordinator.committedEvents.replayCache.map { it.sequence }
            )
        } finally {
            callbacks.shutdownNow()
            coordinator.release()
        }
    }

    @Test
    fun asynchronousStopAcknowledgementStartsReplacementOnceInPhysicalOrder() {
        val engine = FakePlaybackEngine().apply { autoStopAcknowledgement = false }
        val coordinator = PlaybackCoordinator(engine)
        val callbacks = Executors.newSingleThreadExecutor()
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val first = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            coordinator.submit(PlaybackIntent.Stop)
            coordinator.submit(PlaybackIntent.StartStandard(140f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            assertTrue(coordinator.transportState.value is PlaybackTransportState.Stopping)

            callbacks.submit {
                engine.transportObserver?.engineStopped(first)
            }.get(2, TimeUnit.SECONDS)
            assertTrue(coordinator.awaitControlIdle())

            val replacement = coordinator.transportState.value as PlaybackTransportState.Playing
            assertTrue(replacement.context.sessionId != first)
            assertEquals(
                listOf("startStandard", "stopStandard", "startStandard"),
                engine.operations.filter { it == "startStandard" || it == "stopStandard" }
            )
            assertEquals(
                listOf(1L, 2L),
                coordinator.committedEvents.replayCache.map { it.sequence }
            )
        } finally {
            callbacks.shutdownNow()
            coordinator.release()
        }
    }

    @Test
    fun asynchronousUpdateAndSoundCallbacksCannotReviveReplacedSession() {
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        val callbacks = Executors.newSingleThreadExecutor()
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val first = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            coordinator.submit(PlaybackIntent.UpdateStandard(130f, 4, null, false))
            val soundRequest = coordinator.submit(
                PlaybackIntent.SelectSounds(SoundFile.SNARE, SoundFile.COWBELL)
            )
            coordinator.submit(PlaybackIntent.ReplaceStandard(150f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val replacement = (coordinator.transportState.value as PlaybackTransportState.Playing)

            callbacks.submit { engine.completeNextUpdate() }.get(2, TimeUnit.SECONDS)
            callbacks.submit {
                engine.publish(
                    ActiveSoundConfiguration(
                        SoundBank.ACOUSTIC,
                        SoundFile.SNARE,
                        SoundFile.COWBELL
                    ),
                    null,
                    soundRequest,
                    first
                )
            }.get(2, TimeUnit.SECONDS)
            assertTrue(coordinator.awaitControlIdle())

            val current = coordinator.transportState.value as PlaybackTransportState.Playing
            assertEquals(replacement.context.sessionId, current.context.sessionId)
            assertEquals(150f, coordinator.standardConfiguration().bpm)
            assertEquals(SoundFile.CLICK_HI, current.context.audibleSounds?.beatSound)
            assertEquals(1, engine.operations.count { it == "stopStandard" })
        } finally {
            callbacks.shutdownNow()
            coordinator.release()
        }
    }

    @Test
    fun asynchronousRouteAndFocusLossStopEachAffectedSessionExactlyOnce() {
        val reasons = listOf(
            PlaybackInterruptionReason.RouteUnavailable(AudioOutputRoute.BUILT_IN),
            PlaybackInterruptionReason.AudioFocusLost
        )
        reasons.forEach { reason ->
            listOf(false, true).forEach { acknowledgeStart ->
                val engine = FakePlaybackEngine().apply {
                    autoStartAcknowledgement = acknowledgeStart
                    autoStopAcknowledgement = false
                }
                val coordinator = PlaybackCoordinator(engine)
                val callbacks = Executors.newSingleThreadExecutor()
                try {
                    coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
                    assertTrue(coordinator.awaitControlIdle())
                    val session = (coordinator.transportState.value as PlaybackTransportState.SessionState)
                        .context.sessionId
                    callbacks.submit {
                        engine.transportObserver?.engineInterrupted(session, reason)
                    }.get(2, TimeUnit.SECONDS)
                    assertTrue(coordinator.awaitControlIdle())

                    assertEquals(1, engine.operations.count { it == "startStandard" })
                    assertEquals(1, engine.operations.count { it == "stopStandard" })
                    assertTrue(
                        coordinator.transportState.value is PlaybackTransportState.Interrupted ||
                            coordinator.transportState.value is PlaybackTransportState.Failed
                    )
                } finally {
                    callbacks.shutdownNow()
                    coordinator.release()
                }
            }
        }
    }

    @Test
    fun updateCompletionAfterInterruptionOrFailureIsStale() {
        listOf(false, true).forEach { failInsteadOfInterrupt ->
            val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
            val coordinator = PlaybackCoordinator(engine)
            try {
                coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
                assertTrue(coordinator.awaitControlIdle())
                val sessionId = (coordinator.transportState.value as PlaybackTransportState.Playing)
                    .context.sessionId
                val update = coordinator.submit(
                    PlaybackIntent.UpdateStandard(130f, 4, null, false)
                )
                if (failInsteadOfInterrupt) {
                    coordinator.submitSystemInput(
                        PlaybackSystemInput.EngineFailed(sessionId, "backend failed")
                    )
                } else {
                    engine.transportObserver?.engineInterrupted(
                        sessionId,
                        PlaybackInterruptionReason.AudioFocusLost
                    )
                }
                assertTrue(coordinator.awaitControlIdle())
                engine.completeNextUpdate()
                assertTrue(coordinator.awaitControlIdle())
                assertRejectedOutcome(coordinator, update)
            } finally {
                coordinator.release()
            }
        }
    }

    @Test
    fun updateCompletionAfterReleaseCannotCommit() {
        val engine = FakePlaybackEngine().apply { asynchronousUpdates = true }
        val coordinator = PlaybackCoordinator(engine)
        coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
        assertTrue(coordinator.awaitControlIdle())
        val update = coordinator.submit(PlaybackIntent.UpdateStandard(130f, 4, null, false))
        assertTrue(coordinator.awaitControlIdle())

        coordinator.release()
        assertTrue(coordinator.awaitControlIdle())
        engine.completeNextUpdate()

        assertTrue(coordinator.transportState.value is PlaybackTransportState.Idle)
        assertRejectedOutcome(coordinator, update)
    }

    private fun assertRejectedOutcome(coordinator: PlaybackCoordinator, sequence: Long) {
        val outcome = outcomeFor(coordinator, sequence)
        assertTrue("Expected rejected update but was $outcome", outcome is PlaybackIntentOutcome.Rejected)
    }

    private fun assertFailureCode(
        coordinator: PlaybackCoordinator,
        sequence: Long,
        expected: PlaybackCoordinatorFailureCode
    ) {
        val outcome = outcomeFor(coordinator, sequence) as PlaybackIntentOutcome.Rejected
        assertEquals(expected, outcome.failure.code)
    }

    private fun outcomeFor(
        coordinator: PlaybackCoordinator,
        sequence: Long
    ): PlaybackIntentOutcome = coordinator.controlEvents.replayCache
        .filterIsInstance<PlaybackControlEvent.IntentCompleted>()
        .first { it.commandSequence == sequence }
        .outcome

    private fun PlaybackCoordinator.standardConfiguration() =
        ((transportState.value as PlaybackTransportState.Playing).context.configuration as
            CommittedPlaybackConfiguration.Standard)

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
    fun stoppedSoundSelectionPreparesNextSessionWithoutClaimingAudibility() {
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
            assertNull(coordinator.ownership.value.audibleSounds)

            val synth = ActiveSoundConfiguration(
                SoundBank.SYNTH,
                SoundFile.CLICK_HI,
                SoundFile.CLICK_LO
            )
            engine.publish(synth, null)
            assertTrue(coordinator.awaitControlIdle())

            assertNull(coordinator.ownership.value.audibleSounds)
            assertNull(coordinator.ownership.value.soundPreparationFailure)

            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            assertSame(synth, coordinator.ownership.value.audibleSounds)
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

            assertNull(coordinator.ownership.value.audibleSounds)
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
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            coordinator.submit(PlaybackIntent.SelectSoundBank(SoundBank.SYNTH))
            val failure = SoundPreparationFailure(
                SoundBank.SYNTH,
                SoundFile.CLICK_HI,
                SoundPreparationFailureCode.CORRUPT
            )
            engine.publish(original, failure)
            assertTrue(coordinator.awaitControlIdle())

            assertSame(original, coordinator.ownership.value.audibleSounds)
            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            assertSame(original, playing.context.audibleSounds)
            assertSame(failure, coordinator.ownership.value.soundPreparationFailure)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun liveSoundAdoptionUpdatesOwnershipAndTransportTogether() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            val request = coordinator.submit(PlaybackIntent.SelectSoundBank(SoundBank.SYNTH))
            assertTrue(coordinator.awaitControlIdle())
            val synth = ActiveSoundConfiguration(
                SoundBank.SYNTH,
                SoundFile.CLICK_HI,
                SoundFile.CLICK_LO
            )

            engine.publish(synth, null, request, sessionId)
            assertTrue(coordinator.awaitControlIdle())

            assertSame(synth, coordinator.ownership.value.audibleSounds)
            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            assertSame(synth, playing.context.audibleSounds)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun soundAdoptionForSupersededSessionCannotAmendReplacement() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val obsolete = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            val request = coordinator.submit(PlaybackIntent.SelectSoundBank(SoundBank.SYNTH))
            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())
            val replacement = coordinator.transportState.value as PlaybackTransportState.Playing
            val replacementSounds = replacement.context.audibleSounds
            val synth = ActiveSoundConfiguration(
                SoundBank.SYNTH,
                SoundFile.CLICK_HI,
                SoundFile.CLICK_LO
            )

            engine.publish(synth, null, request, obsolete)
            engine.publish(
                synth,
                SoundPreparationFailure(
                    SoundBank.SYNTH,
                    SoundFile.CLICK_HI,
                    SoundPreparationFailureCode.RENDERER_REJECTED
                ),
                request,
                obsolete,
                adopted = false
            )
            assertTrue(coordinator.awaitControlIdle())

            val current = coordinator.transportState.value as PlaybackTransportState.Playing
            assertEquals(replacement.context.sessionId, current.context.sessionId)
            assertSame(replacementSounds, current.context.audibleSounds)
            assertNull(coordinator.ownership.value.soundPreparationFailure)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun staleSoundRequestCannotPublishEvenForCurrentSession() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val sessionId = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId
            val stale = coordinator.submit(PlaybackIntent.SelectSoundBank(SoundBank.SYNTH))
            coordinator.submit(PlaybackIntent.SelectSoundBank(SoundBank.ACOUSTIC))
            assertTrue(coordinator.awaitControlIdle())
            val original = coordinator.ownership.value.audibleSounds
            val synth = ActiveSoundConfiguration(
                SoundBank.SYNTH,
                SoundFile.CLICK_HI,
                SoundFile.CLICK_LO
            )

            engine.publish(synth, null, stale, sessionId)
            assertTrue(coordinator.awaitControlIdle())

            assertSame(original, coordinator.ownership.value.audibleSounds)
            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            assertSame(original, playing.context.audibleSounds)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun soundPreparationFailureUsesOriginatingRequestSequence() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            val request = coordinator.submit(PlaybackIntent.SelectSoundBank(SoundBank.SYNTH))
            coordinator.submit(PlaybackIntent.SetMuted(true))
            assertTrue(coordinator.awaitControlIdle())
            val failure = SoundPreparationFailure(
                SoundBank.SYNTH,
                SoundFile.CLICK_HI,
                SoundPreparationFailureCode.CORRUPT
            )

            engine.publish(engine.activeSounds, failure, request)
            assertTrue(coordinator.awaitControlIdle())

            val event = coordinator.controlEvents.replayCache
                .filterIsInstance<PlaybackControlEvent.SoundPreparationFailed>()
                .single()
            assertEquals(request, event.commandSequence)
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
            val lifecycle = coordinator.lifecycleTransitionsAfter(0)
            assertEquals(
                listOf("Preparing", "Preparing", "Starting", "Playing"),
                lifecycle.transitions.map { it.to::class.simpleName }
            )
            assertEquals(playing, lifecycle.checkpoint.state)
            val scheduled = coordinator.committedEvents.replayCache
                .filterIsInstance<PlaybackCommittedEvent.FirstEventScheduled>()
                .single()
            assertEquals(
                3_216L,
                scheduled.intendedFrame
            )
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun lifecycleJournalRetainsCompleteHistoryBeyondEventReplayCapacity() {
        val engine = FakePlaybackEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            repeat(40) {
                coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
                coordinator.submit(PlaybackIntent.Stop)
            }
            assertTrue(coordinator.awaitControlIdle())

            val lifecycle = coordinator.lifecycleTransitionsAfter(0)
            assertEquals(240, lifecycle.transitions.size)
            assertEquals(
                (1L..240L).toList(),
                lifecycle.transitions.map(PlaybackStateTransition::sequence)
            )
            assertTrue(lifecycle.checkpoint.state is PlaybackTransportState.Idle)
            assertEquals(240L, lifecycle.checkpoint.latestTransitionSequence)
            assertTrue(coordinator.stateTransitions.replayCache.size < lifecycle.transitions.size)

            coordinator.acknowledgeLifecycleTransitionsThrough(120)
            assertEquals(
                (121L..240L).toList(),
                coordinator.lifecycleTransitionsAfter(120)
                    .transitions
                    .map(PlaybackStateTransition::sequence)
            )
            assertThrows(IllegalArgumentException::class.java) {
                coordinator.lifecycleTransitionsAfter(119)
            }
            assertThrows(IllegalArgumentException::class.java) {
                coordinator.acknowledgeLifecycleTransitionsThrough(241)
            }
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun lifecycleJournalReportsGapWhenUnacknowledgedHistoryExceedsSafetyCap() {
        val coordinator = PlaybackCoordinator(FakePlaybackEngine())
        try {
            repeat(700) {
                coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
                coordinator.submit(PlaybackIntent.Stop)
            }
            assertTrue(coordinator.awaitControlIdle())

            val lifecycle = coordinator.lifecycleTransitionsAfter(0)
            assertEquals(4_096, lifecycle.transitions.size)
            assertEquals(
                PlaybackLifecycleGap(0, 105),
                lifecycle.gap
            )
            assertEquals(4_200L, lifecycle.checkpoint.latestTransitionSequence)
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
    fun engineStartFailureRemainsTypedAsStreamStart() {
        val engine = FakePlaybackEngine().apply {
            autoStartAcknowledgement = false
            autoStopAcknowledgement = false
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val starting = coordinator.transportState.value as PlaybackTransportState.Starting

            engine.transportObserver?.engineStartFailed(
                starting.context.sessionId,
                "Audio stream failed to start"
            )
            assertTrue(coordinator.awaitControlIdle())

            val failed = coordinator.transportState.value as PlaybackTransportState.Failed
            assertEquals(
                PlaybackFailureReason.StreamStart("Audio stream failed to start"),
                failed.reason
            )
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
                    PlaybackInterruptionReason.RouteUnavailable(AudioOutputRoute.BUILT_IN)
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
    fun unavailableStartRouteFailsAndClosesStream() {
        val engine = FakePlaybackEngine().apply {
            startRoute = AudioOutputRoute.UNKNOWN
            autoStopAcknowledgement = false
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())

            val failed = coordinator.transportState.value as PlaybackTransportState.Failed
            assertEquals(PlaybackFailureReason.RouteUnavailable, failed.reason)
            assertEquals(1, engine.operations.count { it == "stopStandard" })
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun unclassifiedUsableStartRouteCanCommitPlaying() {
        val engine = FakePlaybackEngine().apply { startRoute = AudioOutputRoute.OTHER }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())

            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            assertEquals(AudioOutputRoute.OTHER, playing.context.route)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun explicitRestartCommitsEvidenceFromNewRoute() {
        val engine = FakePlaybackEngine().apply { startRoute = AudioOutputRoute.BUILT_IN }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val first = coordinator.transportState.value as PlaybackTransportState.Playing

            coordinator.submitSystemInput(
                PlaybackSystemInput.Interrupted(
                    first.context.sessionId,
                    PlaybackInterruptionReason.RouteChanged(
                        AudioOutputRoute.BUILT_IN,
                        AudioOutputRoute.USB
                    )
                )
            )
            assertTrue(coordinator.awaitControlIdle())
            engine.startRoute = AudioOutputRoute.USB
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())

            val restarted = coordinator.transportState.value as PlaybackTransportState.Playing
            assertEquals(AudioOutputRoute.USB, restarted.context.route)
            assertTrue(restarted.context.sessionId != first.context.sessionId)
            assertEquals(2, engine.operations.count { it == "startStandard" })
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun deviceRemovalCallbackInterruptsCoordinatorThroughProductionRoutePolicy() {
        val engine = FakePlaybackEngine().apply { autoStopAcknowledgement = false }
        val coordinator = PlaybackCoordinator(engine)
        lateinit var callback: android.media.AudioDeviceCallback
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            val tracker = ActiveOutputRouteTracker().apply {
                begin(requireNotNull(playing.context.route))
            }
            val monitor = AudioDeviceTopologyMonitor(
                register = { callback = it },
                unregister = {},
                onTopologyChanged = {
                    tracker.observe(AudioOutputRoute.UNKNOWN)?.let { reason ->
                        coordinator.submitSystemInput(
                            PlaybackSystemInput.Interrupted(playing.context.sessionId, reason)
                        )
                    }
                }
            )

            callback.onAudioDevicesRemoved(emptyArray<android.media.AudioDeviceInfo>())
            assertTrue(coordinator.awaitControlIdle())

            val interrupted = coordinator.transportState.value as PlaybackTransportState.Interrupted
            assertEquals(
                PlaybackInterruptionReason.RouteUnavailable(AudioOutputRoute.BUILT_IN),
                interrupted.reason
            )
            assertEquals(1, engine.operations.count { it == "stopStandard" })
            monitor.release()
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
    fun deadAudioTrackWriteCaptureFailsCoordinatorAndStopsSession() {
        val engine = FakePlaybackEngine().apply {
            capturedBackendFailure = AudioBackendFailure(
                AudioBackendOperation.RENDER,
                audioTrackWriteFailureCode(android.media.AudioTrack.ERROR_DEAD_OBJECT)
            )
            autoStopAcknowledgement = false
        }
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (System.nanoTime() < deadline &&
                coordinator.transportState.value !is PlaybackTransportState.Failed) {
                Thread.sleep(5)
            }

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
                    PlaybackInterruptionReason.RouteUnavailable(AudioOutputRoute.BUILT_IN)
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
                    PlaybackInterruptionReason.RouteUnavailable(AudioOutputRoute.BUILT_IN)
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
            PlaybackSystemInput.Interrupted(
                PlaybackSessionId(1),
                PlaybackInterruptionReason.AudioFocusLost
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

    private inner class FakePlaybackEngine : PlaybackEnginePort {
        val operations = Collections.synchronizedList(mutableListOf<String>())
        val callingThreads = Collections.synchronizedSet(mutableSetOf<String>())
        val maximumConcurrentCalls = AtomicInteger()
        val standardStarts = mutableListOf<CommittedPlaybackConfiguration.Standard>()
        private val activeCalls = AtomicInteger()
        var throwOnStart = false
        var throwOnStop = false
        var blockStart = false
        var blockStop = false
        var autoStartAcknowledgement = true
        var startRoute = AudioOutputRoute.BUILT_IN
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
        var capturedBackendFailure: AudioBackendFailure? = null
        var autoStopAcknowledgement = true
        var asynchronousUpdates = false
        var throwOnUpdate = false
        var blockSoundSnapshot = false
        val soundSnapshotEntered = CountDownLatch(1)
        val allowSoundSnapshot = CountDownLatch(1)
        private val updateCompletions = ArrayDeque<
            Pair<PlaybackSessionId, (PlaybackEngineUpdateResult) -> Unit>
        >()

        override var soundPreparationObserver: ((SoundPreparationPublication) -> Unit)? = null
        override var transportObserver: PlaybackEngineTransportObserver? = null
        override var delegate: MetronomeAudioEngineDelegate? = null
        override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate? = null
        override var isMuted: Boolean = false
        override fun prewarmAudioTrack() = call("prewarm")
        override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? =
            capturedBackendFailure?.let(::metricsWithFailure)
        override fun activeSoundConfiguration(): ActiveSoundConfiguration? {
            if (blockSoundSnapshot) {
                soundSnapshotEntered.countDown()
                allowSoundSnapshot.await()
                blockSoundSnapshot = false
            }
            return activeSounds
        }
        override fun soundPreparationFailure(): SoundPreparationFailure? = preparationFailure
        override fun drainRenderedEvents(
            afterCaptureSequence: Long
        ): FrameAudioRenderedEventBatch? =
            renderedBatch?.also { renderedBatch = null }
                ?: capturedBackendFailure?.let {
                    FrameAudioRenderedEventBatch(
                        RenderedEventBatch(emptyList(), afterCaptureSequence, 0),
                        48_000,
                        null
                    )
                }
        override fun selectSounds(
            requestSequence: Long,
            beatResourceId: Int,
            rhythmResourceId: Int
        ) = call("selectSounds")

        override fun selectSoundBank(requestSequence: Long, bank: SoundBank) {
            call("selectSoundBank")
        }

        override fun prepareSounds(
            requestSequence: Long,
            sounds: Collection<SoundFile>
        ) = call("prepareSounds")
        override fun beginStandardSession(
            sessionId: PlaybackSessionId,
            bpm: Float,
            subdivisions: Int,
            accentPattern: List<Boolean>?,
            alternateSixteenth: Boolean
        ) {
            standardStarts += CommittedPlaybackConfiguration.Standard(
                bpm,
                subdivisions,
                accentPattern?.toList(),
                alternateSixteenth,
                isMuted
            )
            call("startStandard") {
                if (throwOnStart) error("start failed")
                if (blockStart) {
                    startEntered.countDown()
                    allowStart.await()
                }
            }
            if (!throwOnStart && autoStartAcknowledgement) publishStarted(sessionId)
        }

        override fun beginPolyrhythmSession(
            sessionId: PlaybackSessionId,
            bpm: Float,
            beats: Int,
            against: Int
        ) {
            call("startPolyrhythm") {
                if (throwOnStart) error("start failed")
            }
            if (!throwOnStart && autoStartAcknowledgement) publishStarted(sessionId)
        }

        override fun updateStandardSession(
            sessionId: PlaybackSessionId,
            configuration: CommittedPlaybackConfiguration.Standard,
            completion: (PlaybackEngineUpdateResult) -> Unit
        ) = call("updateStandard") {
            if (throwOnUpdate) error("asynchronous port failed")
            completeOrQueueUpdate(sessionId, completion)
        }

        override fun updatePolyrhythmSession(
            sessionId: PlaybackSessionId,
            configuration: CommittedPlaybackConfiguration.Polyrhythm,
            completion: (PlaybackEngineUpdateResult) -> Unit
        ) = call("updatePolyrhythm") {
            if (throwOnUpdate) error("asynchronous port failed")
            completeOrQueueUpdate(sessionId, completion)
        }

        override fun stopSession(sessionId: PlaybackSessionId, mode: PlaybackMode) {
            if (throwOnStop) error("stop failed")
            if (blockStop) {
                stopEntered.countDown()
                allowStop.await()
            }
            when (mode) {
                PlaybackMode.STANDARD -> call("stopStandard")
                PlaybackMode.POLYRHYTHM -> call("stopPolyrhythm")
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
                    startRoute,
                    AudioBackendType.AUDIO_TRACK,
                    3_216
                )
            )
        }

        fun completeNextUpdate(
            rejection: PlaybackEngineUpdateResult.Reason? = null,
            diagnostic: String? = null
        ) {
            val (sessionId, completion) = updateCompletions.removeFirst()
            completion(
                if (rejection == null) {
                    PlaybackEngineUpdateResult.Accepted(sessionId)
                } else {
                    PlaybackEngineUpdateResult.Rejected(sessionId, rejection, diagnostic)
                }
            )
        }

        private fun completeOrQueueUpdate(
            sessionId: PlaybackSessionId,
            completion: (PlaybackEngineUpdateResult) -> Unit
        ) {
            if (asynchronousUpdates) {
                updateCompletions.addLast(sessionId to completion)
            } else {
                completion(PlaybackEngineUpdateResult.Accepted(sessionId))
            }
        }

        fun publish(
            active: ActiveSoundConfiguration?,
            failure: SoundPreparationFailure?,
            requestSequence: Long? = null,
            adoptedSessionId: PlaybackSessionId? = null,
            adopted: Boolean = adoptedSessionId != null
        ) {
            activeSounds = active
            preparationFailure = failure
            soundPreparationObserver?.invoke(
                SoundPreparationPublication(
                    requestSequence,
                    adoptedSessionId,
                    adopted,
                    active,
                    failure
                )
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

    private fun metricsWithFailure(failure: AudioBackendFailure) = FrameAudioMetricsSnapshot(
        backend = AudioBackendType.AUDIO_TRACK,
        route = AudioOutputRoute.BUILT_IN,
        sampleRate = 48_000,
        channelCount = 1,
        outputFramesPerBuffer = 192,
        bufferFrames = 384,
        performanceMode = AudioBackendPerformanceMode.LOW_LATENCY,
        bufferSizeInBytes = 768,
        renderChunkFrames = 192,
        estimatedOutputLatencyNanos = 12_000_000,
        queuedClicks = 0,
        queuedBeatClicks = 0,
        queuedRhythmClicks = 0,
        renderedChunks = 0,
        intendedFrames = 0,
        renderedFrames = 0,
        writtenFrames = 0,
        estimatedPresentedFrames = null,
        mixDurationP50UpperBoundNanos = 0,
        mixDurationP95UpperBoundNanos = 0,
        mixDurationP99UpperBoundNanos = 0,
        maximumMixDurationNanos = 0,
        writeDurationP50UpperBoundNanos = 0,
        writeDurationP95UpperBoundNanos = 0,
        writeDurationP99UpperBoundNanos = 0,
        maximumWriteDurationNanos = 0,
        routeChangeCount = 0,
        deadlineMisses = 0,
        droppedEvents = 0,
        maxActiveClicks = 0,
        underrunCount = 0,
        underrunSkippedFrames = 0,
        frameCorrelation = null,
        latestBackendFailure = failure
    )
}
