package com.bfunkstudios.beatclikr.data

import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getAllSongs(): Flow<List<Song>>
    suspend fun upsert(song: Song)
    suspend fun delete(song: Song)
}
