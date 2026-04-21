package com.bfunkstudios.beatclikr.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bfunkstudios.beatclikr.data.Song
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getCount(): Int
}
