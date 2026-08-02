package com.bfunkstudios.beatclikr.data

data class PracticeItemSnapshot(
    val itemId: String,
    val title: String,
    val artist: String,
    val beatsPerMinute: Float?,
    val beatsPerMeasure: Int?,
    val groove: Groove?
) {
    companion object {
        fun fromSong(song: Song) = PracticeItemSnapshot(
            song.id.toString(),
            song.title,
            song.artist,
            song.beatsPerMinute,
            song.beatsPerMeasure,
            song.groove
        )

        fun metronome() = PracticeItemSnapshot(
            PracticedSong.METRONOME_SONG_ID,
            "Metronome",
            "BeatClikr",
            null,
            null,
            null
        )

        fun polyrhythm() = PracticeItemSnapshot(
            PracticedSong.POLYRHYTHM_SONG_ID,
            "Polyrhythm",
            "BeatClikr",
            null,
            null,
            null
        )
    }
}
