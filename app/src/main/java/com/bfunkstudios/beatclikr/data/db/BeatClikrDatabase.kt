package com.bfunkstudios.beatclikr.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bfunkstudios.beatclikr.data.Playlist
import com.bfunkstudios.beatclikr.data.PlaylistEntry
import com.bfunkstudios.beatclikr.data.PracticeAccountingCheckpoint
import com.bfunkstudios.beatclikr.data.PracticedSong
import com.bfunkstudios.beatclikr.data.PracticeSession
import com.bfunkstudios.beatclikr.data.Song

@Database(
    entities = [
        Song::class,
        Playlist::class,
        PlaylistEntry::class,
        PracticeSession::class,
        PracticedSong::class,
        PracticeAccountingCheckpoint::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BeatClikrDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun practiceHistoryDao(): PracticeHistoryDao
}
