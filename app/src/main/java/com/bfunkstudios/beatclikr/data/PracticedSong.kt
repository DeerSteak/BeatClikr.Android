package com.bfunkstudios.beatclikr.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "practiced_songs",
    foreignKeys = [ForeignKey(
        entity = PracticeSession::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("session_id")]
)
data class PracticedSong(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "session_id") val sessionId: UUID,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "beats_per_minute") val beatsPerMinute: Float?,
    @ColumnInfo(name = "beats_per_measure") val beatsPerMeasure: Int?,
    val groove: Groove?,
    @ColumnInfo(name = "times_practiced") val timesPracticed: Int = 1,
    @ColumnInfo(name = "song_id") val songId: String
) {
    companion object {
        const val METRONOME_SONG_ID = "beatclikr.metronome"
        const val POLYRHYTHM_SONG_ID = "beatclikr.polyrhythm"

        fun fromSong(song: Song, sessionId: UUID) = PracticedSong(
            sessionId = sessionId,
            title = song.title,
            artist = song.artist,
            beatsPerMinute = song.beatsPerMinute,
            beatsPerMeasure = song.beatsPerMeasure,
            groove = song.groove,
            songId = song.id.toString()
        )

        fun metronome(sessionId: UUID) = PracticedSong(
            sessionId = sessionId,
            title = "Metronome",
            artist = "BeatClikr",
            beatsPerMinute = null,
            beatsPerMeasure = null,
            groove = null,
            songId = METRONOME_SONG_ID
        )

        fun polyrhythm(sessionId: UUID) = PracticedSong(
            sessionId = sessionId,
            title = "Polyrhythm",
            artist = "BeatClikr",
            beatsPerMinute = null,
            beatsPerMeasure = null,
            groove = null,
            songId = POLYRHYTHM_SONG_ID
        )
    }
}
