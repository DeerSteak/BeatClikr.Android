package com.bfunkstudios.beatclikr

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bfunkstudios.beatclikr.data.PracticeAccountingCheckpoint
import com.bfunkstudios.beatclikr.data.PracticeDayIdentity
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepositoryImpl
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.data.db.BeatClikrDatabase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeHistoryRepositoryInstrumentedTest {
    private lateinit var database: BeatClikrDatabase
    private lateinit var repository: PracticeHistoryRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BeatClikrDatabase::class.java
        ).build()
        repository = PracticeHistoryRepositoryImpl(
            database.practiceHistoryDao(),
            FakePracticeReminderScheduler()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun retryIsIdempotentAndHistoryAppearsOnlyAtThirtySeconds() = runTest {
        val day = PracticeDayIdentity.capture(
            Instant.parse("2026-08-02T18:00:00Z"),
            ZoneId.of("America/Chicago")
        )
        val item = PracticeItemSnapshot.metronome()
        val started = activeCheckpoint(sequence = 1, elapsedNanos = 1_000)
        val tenSeconds = activeCheckpoint(sequence = 1, elapsedNanos = 10_000)

        repository.applyAccountingUpdate(day, item, 0, 1, started)
        repository.applyAccountingUpdate(day, item, 10_000_000_000L, 0, tenSeconds)
        repository.applyAccountingUpdate(day, item, 10_000_000_000L, 0, tenSeconds)

        assertTrue(repository.getAllSessions().first().isEmpty())

        repository.applyAccountingUpdate(
            day,
            item,
            20_000_000_000L,
            0,
            PracticeAccountingCheckpoint(acknowledgedLifecycleSequence = 2)
        )

        val practiced = repository.getAllSessions().first().single().songs.single()
        assertEquals(30_000_000_000L, practiced.durationNanos)
        assertEquals(1, practiced.timesPracticed)
    }

    @Test
    fun recoveryResetAllowsNewProcessSequence() = runTest {
        repository.applyAccountingUpdate(
            null,
            null,
            0,
            0,
            PracticeAccountingCheckpoint(acknowledgedLifecycleSequence = 9)
        )

        repository.resetAccountingCheckpoint(
            PracticeAccountingCheckpoint(acknowledgedLifecycleSequence = 0)
        )
        repository.applyAccountingUpdate(
            null,
            null,
            0,
            0,
            PracticeAccountingCheckpoint(acknowledgedLifecycleSequence = 1)
        )

        assertEquals(1L, repository.getAccountingCheckpoint()?.acknowledgedLifecycleSequence)
    }

    private fun activeCheckpoint(sequence: Long, elapsedNanos: Long) =
        PracticeAccountingCheckpoint(
            acknowledgedLifecycleSequence = sequence,
            activePlaybackSessionId = 1,
            activeItemId = PracticeItemSnapshot.metronome().itemId,
            lastCheckpointElapsedNanos = elapsedNanos,
            periodCounted = true
        )
}
