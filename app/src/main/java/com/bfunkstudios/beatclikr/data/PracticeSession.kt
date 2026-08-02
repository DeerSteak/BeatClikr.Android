package com.bfunkstudios.beatclikr.data

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Entity(
    tableName = "practice_sessions",
    indices = [Index(value = ["local_day_key"], unique = true)]
)
data class PracticeSession(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val date: Long,
    @Embedded val dayIdentity: PracticeDayIdentity = PracticeDayIdentity.capture(
        Instant.ofEpochMilli(date),
        ZoneId.systemDefault()
    )
)
