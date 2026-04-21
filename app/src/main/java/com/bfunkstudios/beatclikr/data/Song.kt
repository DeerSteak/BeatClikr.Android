package com.bfunkstudios.beatclikr.data

import java.util.UUID

data class Song(
    var title: String,
    var artist: String,
    var beatsPerMinute: Float,
    var beatsPerMeasure: Int,
    var subdivisions: Subdivisions,
    var liveSequence: Int?,
    var rehearsalSequence: Int?,
    val id: UUID = UUID.randomUUID()
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