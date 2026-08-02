package com.bfunkstudios.beatclikr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bfunkstudios.beatclikr.data.PracticeAccountingCheckpoint
import com.bfunkstudios.beatclikr.data.PracticeDayIdentity
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.data.PracticedSong
import com.bfunkstudios.beatclikr.data.PracticeSession
import com.bfunkstudios.beatclikr.data.PracticeSessionWithSongs
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeHistoryDao {

    @Transaction
    @Query("""SELECT * FROM practice_sessions WHERE EXISTS (
        SELECT 1 FROM practiced_songs
        WHERE practiced_songs.session_id = practice_sessions.id
        AND practiced_songs.duration_nanos >= 30000000000
    ) ORDER BY date DESC""")
    fun getAllSessions(): Flow<List<PracticeSessionWithSongs>>

    @Query("SELECT * FROM practice_sessions WHERE local_day_key = :localDayKey LIMIT 1")
    suspend fun getSessionForLocalDay(localDayKey: String): PracticeSession?

    @Query("SELECT * FROM practice_sessions WHERE date >= :start AND date < :end LIMIT 1")
    suspend fun getSessionForDay(start: Long, end: Long): PracticeSession?

    @Transaction
    @Query("SELECT * FROM practice_sessions WHERE date >= :start AND date < :end LIMIT 1")
    suspend fun getSessionWithSongsForDay(start: Long, end: Long): PracticeSessionWithSongs?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: PracticeSession)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionIfAbsent(session: PracticeSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPracticedSong(song: PracticedSong)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPracticedSongIfAbsent(song: PracticedSong): Long

    @Query("""UPDATE practiced_songs SET
        duration_nanos = duration_nanos + :durationNanos,
        times_practiced = times_practiced + :periodIncrement
        WHERE session_id = :sessionId AND song_id = :itemId""")
    suspend fun addPractice(
        sessionId: java.util.UUID,
        itemId: String,
        durationNanos: Long,
        periodIncrement: Int
    )

    @Query("SELECT * FROM practice_accounting_checkpoint WHERE id = 1")
    suspend fun getAccountingCheckpoint(): PracticeAccountingCheckpoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccountingCheckpoint(checkpoint: PracticeAccountingCheckpoint)

    @Update
    suspend fun updatePracticedSong(song: PracticedSong)

    @Transaction
    suspend fun applyAccountingUpdate(
        day: PracticeDayIdentity?,
        item: PracticeItemSnapshot?,
        durationNanos: Long,
        periodIncrement: Int,
        checkpoint: PracticeAccountingCheckpoint
    ) {
        require(durationNanos >= 0)
        require(periodIncrement in 0..1)
        val existingCheckpoint = getAccountingCheckpoint()
        val existingSequence = existingCheckpoint?.acknowledgedLifecycleSequence ?: -1L
        if (checkpoint.acknowledgedLifecycleSequence < existingSequence) return
        if (checkpoint.acknowledgedLifecycleSequence == existingSequence &&
            checkpoint.activePlaybackSessionId != null &&
            (checkpoint.lastCheckpointElapsedNanos ?: Long.MIN_VALUE) <=
            (existingCheckpoint?.lastCheckpointElapsedNanos ?: Long.MIN_VALUE)) {
            return
        }
        if (day != null && item != null) {
            val proposedSession = PracticeSession(
                date = day.originalTimestampMillis,
                dayIdentity = day
            )
            insertSessionIfAbsent(proposedSession)
            val session = requireNotNull(getSessionForLocalDay(day.localDayKey))
            insertPracticedSongIfAbsent(
                PracticedSong(
                    sessionId = session.id,
                    title = item.title,
                    artist = item.artist,
                    beatsPerMinute = item.beatsPerMinute,
                    beatsPerMeasure = item.beatsPerMeasure,
                    groove = item.groove,
                    timesPracticed = 0,
                    songId = item.itemId,
                    durationNanos = 0
                )
            )
            addPractice(session.id, item.itemId, durationNanos, periodIncrement)
        }
        upsertAccountingCheckpoint(checkpoint)
    }
}
