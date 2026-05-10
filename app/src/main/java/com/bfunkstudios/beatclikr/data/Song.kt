package com.bfunkstudios.beatclikr.data

import androidx.room.ColumnInfo
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
    @ColumnInfo(name = "subdivisions")
    var groove: Groove,
    var liveSequence: Int?,
    var rehearsalSequence: Int?,
    var beatPattern: BeatPattern? = null
) {
    companion object {
        fun instantSong() = Song(
            title = "Instant",
            artist = "Song",
            beatsPerMinute = 120f,
            beatsPerMeasure = 4,
            groove = Groove.Quarter,
            liveSequence = null,
            rehearsalSequence = null
        )
    }
}
