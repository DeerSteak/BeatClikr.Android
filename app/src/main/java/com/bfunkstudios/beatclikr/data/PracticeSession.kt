package com.bfunkstudios.beatclikr.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "practice_sessions")
data class PracticeSession(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val date: Long
)
