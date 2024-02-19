package com.bfunkstudios.beatclikr.data

import android.util.Log
import java.util.UUID

object DataSource {
    var songs = listOf(
        Song(
            "Jump",
            "Van Halen",
            129f,
            4,
            Subdivisions.Eighth,
            null,
            null,
            UUID.randomUUID()),
        Song(
            "Good Enough",
            "Van Halen",
            141f,
            4,
            Subdivisions.Quarter,
            null,
            null,
            UUID.randomUUID()
        ),
        Song(
            "Panama",
            "Van Halen",
            141f,
            4,
            Subdivisions.Quarter,
            null,
            null,
            UUID.randomUUID()
        ),
        Song(
            "Right Now",
            "Van Halen",
            94f,
            4,
            Subdivisions.Quarter,
            null,
            null,
            UUID.randomUUID()
        ),
        Song(
            "Top of the World",
            "Van Halen",
            128f,
            4,
            Subdivisions.Quarter,
            null,
            null,
            UUID.randomUUID()
        )
    )

    fun saveSong(song: Song) {
        for (s in songs) {
            Log.d("DataSource", "Title: " + s.title + ", ID: " + s.id)
        }

        val existingSong = songs.find{ it.id == song.id }
        Log.d("DataSource", "Existing song ID: " + existingSong?.id)
        Log.d("DataSource", "Incoming song ID: " + song.id)
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