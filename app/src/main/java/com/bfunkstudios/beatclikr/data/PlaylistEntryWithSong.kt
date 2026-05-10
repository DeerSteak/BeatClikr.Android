package com.bfunkstudios.beatclikr.data

import androidx.room.Embedded
import androidx.room.Relation

data class PlaylistEntryWithSong(
    @Embedded val entry: PlaylistEntry,
    @Relation(
        parentColumn = "song_id",
        entityColumn = "id"
    )
    val song: Song
)
