package com.bfunkstudios.beatclikr.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    var title: String,
    var artist: String,
    var beatsPerMinute: Float,
    var beatsPerMeasure: Int,
    var subdivisions: Subdivisions,
    var liveSequence: Int?,
    var rehearsalSequence: Int?
) {
    companion object {
        fun instantSong() = Song(
            title = "Instant",
            artist = "Song",
            beatsPerMinute = 120f,
            beatsPerMeasure = 4,
            subdivisions = Subdivisions.Quarter,
            liveSequence = null,
            rehearsalSequence = null
        )
    }
}
