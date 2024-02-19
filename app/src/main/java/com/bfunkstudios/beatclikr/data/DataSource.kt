package com.bfunkstudios.beatclikr.data

object DataSource {
    var songs = listOf(
        Song(
            "Jump",
            "Van Halen",
            129f,
            4,
            Subdivisions.Eighth,
            null,
            null),
        Song(
            "Good Enough",
            "Van Halen",
            141f,
            4,
            Subdivisions.Quarter,
            null,
            null
        ),
        Song(
            "Panama",
            "Van Halen",
            141f,
            4,
            Subdivisions.Quarter,
            null,
            null
        ),
        Song(
            "Right Now",
            "Van Halen",
            94f,
            4,
            Subdivisions.Quarter,
            null,
            null
        ),
        Song(
            "Top of the World",
            "Van Halen",
            128f,
            4,
            Subdivisions.Quarter,
            null,
            null
        )
    )

    fun saveSong(song: Song) {
        val existingSong = songs.find{ it.id == song.id }
        if (existingSong == null) {
            songs = songs + song
        } else {
            existingSong.artist = song.artist
            existingSong.title = song.title
            existingSong.beatsPerMinute = song.beatsPerMinute
            existingSong.beatsPerMeasure = song.beatsPerMeasure
            existingSong.subdivisions = song.subdivisions
            existingSong.liveSequence = song.liveSequence
            existingSong.rehearsalSequence = song.rehearsalSequence
        }
    }
}