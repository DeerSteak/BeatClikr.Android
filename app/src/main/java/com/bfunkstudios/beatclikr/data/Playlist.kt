package com.bfunkstudios.beatclikr.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
