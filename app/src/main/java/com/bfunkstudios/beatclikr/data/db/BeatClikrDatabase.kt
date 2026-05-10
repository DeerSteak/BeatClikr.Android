package com.bfunkstudios.beatclikr.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bfunkstudios.beatclikr.data.Song

@Database(entities = [Song::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class BeatClikrDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN beatPattern TEXT")
            }
        }
    }
}
