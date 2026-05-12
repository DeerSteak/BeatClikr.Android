package com.bfunkstudios.beatclikr.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlist_id"),
        Index("song_id")
    ]
)
data class PlaylistEntry(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "playlist_id") val playlistId: UUID,
    @ColumnInfo(name = "song_id") val songId: UUID,
    val sequence: Int
)
