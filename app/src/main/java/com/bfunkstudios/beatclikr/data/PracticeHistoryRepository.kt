package com.bfunkstudios.beatclikr.data

import kotlinx.coroutines.flow.Flow

interface PracticeHistoryRepository {
    fun getAllSessions(): Flow<List<PracticeSessionWithSongs>>
    suspend fun getAccountingCheckpoint(): PracticeAccountingCheckpoint?
    suspend fun resetAccountingCheckpoint(checkpoint: PracticeAccountingCheckpoint)
    suspend fun applyAccountingUpdate(
        day: PracticeDayIdentity?,
        item: PracticeItemSnapshot?,
        durationNanos: Long,
        periodIncrement: Int,
        checkpoint: PracticeAccountingCheckpoint
    )
}
