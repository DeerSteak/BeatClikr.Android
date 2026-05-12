package com.bfunkstudios.beatclikr.data

import kotlinx.coroutines.flow.Flow

interface PracticeHistoryRepository {
    fun getAllSessions(): Flow<List<PracticeSessionWithSongs>>
    suspend fun recordSongPlayed(song: Song)
    suspend fun recordMetronomePractice()
    suspend fun recordPolyrhythmPractice()
}
