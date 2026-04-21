package com.bfunkstudios.beatclikr.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bfunkstudios.beatclikr.data.Song

@Database(entities = [Song::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class BeatClikrDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}
