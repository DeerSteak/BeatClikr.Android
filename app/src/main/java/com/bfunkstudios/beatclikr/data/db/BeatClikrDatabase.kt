package com.bfunkstudios.beatclikr.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bfunkstudios.beatclikr.data.Playlist
import com.bfunkstudios.beatclikr.data.PlaylistEntry
import com.bfunkstudios.beatclikr.data.Song

@Database(entities = [Song::class, Playlist::class, PlaylistEntry::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class BeatClikrDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN beatPattern TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_entries` (`id` TEXT NOT NULL, `playlist_id` TEXT NOT NULL, `song_id` TEXT NOT NULL, `sequence` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`song_id`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entries_playlist_id` ON `playlist_entries` (`playlist_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entries_song_id` ON `playlist_entries` (`song_id`)")
            }
        }
    }
}
