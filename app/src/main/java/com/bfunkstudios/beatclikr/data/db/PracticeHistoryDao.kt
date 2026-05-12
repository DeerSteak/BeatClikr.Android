package com.bfunkstudios.beatclikr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bfunkstudios.beatclikr.data.PracticedSong
import com.bfunkstudios.beatclikr.data.PracticeSession
import com.bfunkstudios.beatclikr.data.PracticeSessionWithSongs
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeHistoryDao {

    @Transaction
    @Query("SELECT * FROM practice_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<PracticeSessionWithSongs>>

    @Query("SELECT * FROM practice_sessions WHERE date >= :start AND date < :end LIMIT 1")
    suspend fun getSessionForDay(start: Long, end: Long): PracticeSession?

    @Transaction
    @Query("SELECT * FROM practice_sessions WHERE date >= :start AND date < :end LIMIT 1")
    suspend fun getSessionWithSongsForDay(start: Long, end: Long): PracticeSessionWithSongs?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: PracticeSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPracticedSong(song: PracticedSong)

    @Update
    suspend fun updatePracticedSong(song: PracticedSong)
}
