package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.PracticeAccountingCheckpoint
import com.bfunkstudios.beatclikr.data.PracticeDayIdentity
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepository
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.data.PracticeSessionWithSongs
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeAccountingCoordinatorTest {
    @Test
    fun playingLifecycleCountsOnePeriodAndOnlyMonotonicPlayingDuration() = runTest {
        val lifecycle = FakeLifecycle()
        val repository = FakeRepository()
        val item = PracticeItemSnapshot("song-1", "Song", "Artist", 120f, 4, null)
        PracticeAccountingCoordinator(lifecycle, repository, backgroundScope).start()
        runCurrent()

        lifecycle.publish(transition(1, PlaybackTransportState.Idle, playing(1, item), 1_000))
        runCurrent()
        lifecycle.publish(transition(2, playing(1, item), playing(1, item), 6_000))
        runCurrent()
        lifecycle.publish(transition(3, playing(1, item), PlaybackTransportState.Idle, 11_000))
        runCurrent()

        assertEquals(listOf(1, 0, 0), repository.updates.map { it.periodIncrement })
        assertEquals(listOf(0L, 5_000L, 5_000L), repository.updates.map { it.durationNanos })
        assertEquals(listOf("2026-04-04", "2026-04-04", "2026-04-04"), repository.updates.map { it.day?.localDayKey })
        assertEquals(3, lifecycle.acknowledged)
        assertNull(repository.checkpoint?.activePlaybackSessionId)
    }

    @Test
    fun failedStartAndDuplicatePlayingSessionDoNotCreateExtraPeriods() = runTest {
        val lifecycle = FakeLifecycle()
        val repository = FakeRepository()
        val item = PracticeItemSnapshot.metronome()
        PracticeAccountingCoordinator(lifecycle, repository, backgroundScope).start()
        runCurrent()
        val preparing = sessionState(PlaybackTransportState::Preparing, 1, item)
        val failed = PlaybackTransportState.Failed(
            preparing.context,
            PlaybackFailureReason.AudioFocusUnavailable
        )

        lifecycle.publish(transition(1, PlaybackTransportState.Idle, preparing, 1_000))
        runCurrent()
        lifecycle.publish(transition(2, preparing, failed, 2_000))
        runCurrent()
        lifecycle.publish(transition(3, failed, playing(2, item), 3_000))
        runCurrent()
        lifecycle.publish(transition(4, playing(2, item), playing(2, item), 4_000))
        runCurrent()

        assertEquals(1, repository.updates.count { it.periodIncrement == 1 })
        assertEquals(1_000L, repository.updates.sumOf { it.durationNanos })
    }

    @Test
    fun checkpointsAttributeWholeIntervalsToTheCurrentCivilContext() = runTest {
        val lifecycle = FakeLifecycle()
        val repository = FakeRepository()
        val item = PracticeItemSnapshot.metronome()
        PracticeAccountingCoordinator(lifecycle, repository, backgroundScope).start()
        runCurrent()

        lifecycle.publish(transition(
            1,
            PlaybackTransportState.Idle,
            playing(1, item),
            1_000,
            "2026-03-09T04:59:00Z",
            "America/Chicago"
        ))
        runCurrent()
        lifecycle.publish(transition(
            2,
            playing(1, item),
            playing(1, item),
            11_000,
            "2026-03-09T05:01:00Z",
            "America/Chicago"
        ))
        runCurrent()
        lifecycle.publish(transition(
            3,
            playing(1, item),
            playing(1, item),
            21_000,
            "2026-03-09T16:00:00Z",
            "Asia/Tokyo"
        ))
        runCurrent()

        assertEquals(
            listOf("2026-03-08", "2026-03-09", "2026-03-10"),
            repository.updates.map { it.day?.localDayKey }
        )
        assertEquals(listOf(0L, 10_000L, 10_000L), repository.updates.map { it.durationNanos })
    }

    @Test
    fun periodicCheckpointPersistsAnUninterruptedPlayingInterval() = runTest {
        val lifecycle = FakeLifecycle()
        val repository = FakeRepository()
        var elapsedNow = 6_000L
        val day = PracticeDayIdentity.capture(
            Instant.parse("2026-08-02T18:00:00Z"),
            java.time.ZoneId.of("America/Chicago")
        )
        PracticeAccountingCoordinator(
            lifecycle,
            repository,
            backgroundScope,
            { elapsedNow },
            { day },
            1_000
        ).start()
        runCurrent()
        val item = PracticeItemSnapshot.metronome()
        lifecycle.publish(transition(1, PlaybackTransportState.Idle, playing(1, item), 1_000))
        runCurrent()

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(5_000L, repository.updates.last().durationNanos)
        assertEquals(1L, repository.checkpoint?.activePlaybackSessionId)
    }

    @Test
    fun processRecreationClearsPersistedAuthorityWithoutAddingPractice() = runTest {
        val lifecycle = FakeLifecycle()
        val repository = FakeRepository().apply {
            checkpoint = PracticeAccountingCheckpoint(
                acknowledgedLifecycleSequence = 9,
                activePlaybackSessionId = 4,
                activeItemId = PracticeItemSnapshot.metronome().itemId,
                lastCheckpointElapsedNanos = 50_000,
                periodCounted = true
            )
        }

        PracticeAccountingCoordinator(lifecycle, repository, backgroundScope).start()
        runCurrent()

        assertEquals(1, repository.updates.size)
        assertEquals(0L, repository.updates.single().durationNanos)
        assertNull(repository.checkpoint?.activePlaybackSessionId)
        assertEquals(0L, repository.checkpoint?.acknowledgedLifecycleSequence)
    }

    @Test
    fun lifecycleJournalGapClosesAuthorityPublishesDiagnosticAndContinuesCollecting() = runTest {
        val lifecycle = FakeLifecycle()
        val repository = FakeRepository()
        val coordinator = PracticeAccountingCoordinator(lifecycle, repository, backgroundScope)
        coordinator.start()
        runCurrent()
        val item = PracticeItemSnapshot.metronome()
        lifecycle.publish(transition(1, PlaybackTransportState.Idle, playing(1, item), 1_000))
        runCurrent()

        lifecycle.forceGap(10, PlaybackTransportState.Idle, 8)
        runCurrent()

        assertNull(repository.checkpoint?.activePlaybackSessionId)
        assertEquals(10L, repository.checkpoint?.acknowledgedLifecycleSequence)
        assertEquals(10L, lifecycle.acknowledged)
        assertTrue(
            coordinator.diagnostic.value is PracticeAccountingDiagnostic.LifecycleJournalGap
        )

        lifecycle.publish(transition(11, PlaybackTransportState.Idle, playing(2, item), 20_000))
        runCurrent()

        assertEquals(2, repository.updates.count { it.periodIncrement == 1 })
        assertEquals(11L, lifecycle.acknowledged)
        assertEquals(2L, repository.checkpoint?.activePlaybackSessionId)
    }

    private fun transition(
        sequence: Long,
        from: PlaybackTransportState,
        to: PlaybackTransportState,
        elapsed: Long,
        wallInstant: String = "2026-04-04T18:00:00Z",
        timeZoneIdentifier: String = "America/Chicago"
    ) = PlaybackStateTransition(
        sequence,
        from,
        to,
        elapsed,
        Instant.parse(wallInstant).toEpochMilli(),
        timeZoneIdentifier
    )

    private fun playing(
        sessionId: Long,
        item: PracticeItemSnapshot
    ): PlaybackTransportState.Playing {
        val preparing = sessionState(PlaybackTransportState::Preparing, sessionId, item)
        return PlaybackTransportState.Playing(
            preparing.context.copy(
                audibleSounds = SOUNDS,
                route = AudioOutputRoute.BUILT_IN,
                backend = AudioBackendType.AUDIO_TRACK
            )
        )
    }

    private fun <T : PlaybackTransportState.SessionState> sessionState(
        factory: (PlaybackSessionContext) -> T,
        sessionId: Long,
        item: PracticeItemSnapshot
    ) = factory(
        PlaybackSessionContext(
            PlaybackSessionId(sessionId),
            PlaybackMode.STANDARD,
            CommittedPlaybackConfiguration.Standard(120f, 4, null, false, false),
            startOrigin = PlaybackStartOrigin.USER,
            practiceItem = item
        )
    )

    private class FakeLifecycle : PlaybackLifecycleObservation {
        private val journal = mutableListOf<PlaybackStateTransition>()
        override val lifecycleCheckpoint = MutableStateFlow(
            PlaybackLifecycleCheckpoint(0, PlaybackTransportState.Idle)
        )
        var acknowledged = 0L
        private var gap: PlaybackLifecycleGap? = null

        fun publish(transition: PlaybackStateTransition) {
            journal += transition
            lifecycleCheckpoint.value = PlaybackLifecycleCheckpoint(
                transition.sequence,
                transition.to
            )
        }

        fun forceGap(
            sequence: Long,
            state: PlaybackTransportState,
            oldestAvailableSequence: Long
        ) {
            gap = PlaybackLifecycleGap(acknowledged, oldestAvailableSequence)
            lifecycleCheckpoint.value = PlaybackLifecycleCheckpoint(sequence, state)
        }

        override fun lifecycleTransitionsAfter(sequence: Long) = PlaybackLifecycleBatch(
            journal.filter { it.sequence > sequence },
            lifecycleCheckpoint.value,
            gap
        )

        override fun acknowledgeLifecycleTransitionsThrough(sequence: Long) {
            acknowledged = sequence
            gap = null
        }
    }

    private class FakeRepository : PracticeHistoryRepository {
        val updates = mutableListOf<Update>()
        var checkpoint: PracticeAccountingCheckpoint? = null
        override fun getAllSessions(): Flow<List<PracticeSessionWithSongs>> = emptyFlow()
        override suspend fun getAccountingCheckpoint() = checkpoint
        override suspend fun applyAccountingUpdate(
            day: PracticeDayIdentity?,
            item: PracticeItemSnapshot?,
            durationNanos: Long,
            periodIncrement: Int,
            checkpoint: PracticeAccountingCheckpoint
        ) {
            updates += Update(day, durationNanos, periodIncrement)
            this.checkpoint = checkpoint
        }
    }

    private data class Update(
        val day: PracticeDayIdentity?,
        val durationNanos: Long,
        val periodIncrement: Int
    )

    private companion object {
        val SOUNDS = ActiveSoundConfiguration(
            com.bfunkstudios.beatclikr.data.SoundBank.ACOUSTIC,
            com.bfunkstudios.beatclikr.data.SoundFile.CLICK_HI,
            com.bfunkstudios.beatclikr.data.SoundFile.CLICK_LO
        )
    }
}
