package com.bfunkstudios.beatclikr.data

import androidx.room.Embedded
import androidx.room.Relation

data class PlaylistWithEntries(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
        entity = PlaylistEntry::class
    )
    val entries: List<PlaylistEntryWithSong>
)
