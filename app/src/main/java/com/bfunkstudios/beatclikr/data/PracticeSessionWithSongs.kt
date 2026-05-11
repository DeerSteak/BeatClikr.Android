package com.bfunkstudios.beatclikr.data

import androidx.room.Embedded
import androidx.room.Relation

data class PracticeSessionWithSongs(
    @Embedded val session: PracticeSession,
    @Relation(
        parentColumn = "id",
        entityColumn = "session_id"
    )
    val songs: List<PracticedSong>
)
