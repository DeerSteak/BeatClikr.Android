package com.bfunkstudios.beatclikr.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bfunkstudios.beatclikr.data.PracticeDayIdentity
import java.time.Instant
import java.time.ZoneId

object BeatClikrMigrations {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addPracticeColumns(db)
            populateCivilDays(db)
            mergeDuplicateDays(db)
            mergeDuplicateItems(db)
            normalizePlaylistSequences(db)
            createConstraintsAndCheckpoint(db)
        }
    }

    private fun addPracticeColumns(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN local_day_key TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN time_zone_identifier TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN utc_offset_seconds INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN calendar_identifier TEXT NOT NULL DEFAULT 'gregorian'")
        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN original_timestamp_millis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE practiced_songs ADD COLUMN duration_nanos INTEGER NOT NULL DEFAULT 30000000000")
    }

    private fun populateCivilDays(db: SupportSQLiteDatabase) {
        val zone = ZoneId.systemDefault()
        db.query("SELECT id, date FROM practice_sessions").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val dateIndex = cursor.getColumnIndexOrThrow("date")
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val date = cursor.getLong(dateIndex)
                val identity = PracticeDayIdentity.capture(Instant.ofEpochMilli(date), zone)
                db.execSQL(
                    """UPDATE practice_sessions SET local_day_key = ?, time_zone_identifier = ?,
                        utc_offset_seconds = ?, calendar_identifier = ?, original_timestamp_millis = ?
                        WHERE id = ?""".trimIndent(),
                    arrayOf(
                        identity.localDayKey,
                        identity.timeZoneIdentifier,
                        identity.utcOffsetSeconds,
                        identity.calendarIdentifier,
                        identity.originalTimestampMillis,
                        id
                    )
                )
            }
        }
    }

    private fun mergeDuplicateDays(db: SupportSQLiteDatabase) {
        db.execSQL(
            """UPDATE practiced_songs SET session_id = (
                SELECT MIN(canonical.id) FROM practice_sessions canonical
                JOIN practice_sessions original ON canonical.local_day_key = original.local_day_key
                WHERE original.id = practiced_songs.session_id
            )""".trimIndent()
        )
        db.execSQL(
            """DELETE FROM practice_sessions WHERE id NOT IN (
                SELECT MIN(id) FROM practice_sessions GROUP BY local_day_key
            )""".trimIndent()
        )
    }

    private fun mergeDuplicateItems(db: SupportSQLiteDatabase) {
        db.execSQL(
            """UPDATE practiced_songs SET
                times_practiced = (
                    SELECT SUM(other.times_practiced) FROM practiced_songs other
                    WHERE other.session_id = practiced_songs.session_id
                    AND other.song_id = practiced_songs.song_id
                ),
                duration_nanos = (
                    SELECT SUM(other.duration_nanos) FROM practiced_songs other
                    WHERE other.session_id = practiced_songs.session_id
                    AND other.song_id = practiced_songs.song_id
                )
                WHERE id IN (
                    SELECT MIN(id) FROM practiced_songs GROUP BY session_id, song_id
                )""".trimIndent()
        )
        db.execSQL(
            """DELETE FROM practiced_songs WHERE id NOT IN (
                SELECT MIN(id) FROM practiced_songs GROUP BY session_id, song_id
            )""".trimIndent()
        )
    }

    private fun createConstraintsAndCheckpoint(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE UNIQUE INDEX index_playlist_entries_playlist_id_sequence ON playlist_entries(playlist_id, sequence)")
        db.execSQL("CREATE UNIQUE INDEX index_practice_sessions_local_day_key ON practice_sessions(local_day_key)")
        db.execSQL("CREATE UNIQUE INDEX index_practiced_songs_session_id_song_id ON practiced_songs(session_id, song_id)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS practice_accounting_checkpoint (
                id INTEGER NOT NULL PRIMARY KEY,
                acknowledged_lifecycle_sequence INTEGER NOT NULL,
                active_playback_session_id INTEGER,
                active_item_id TEXT,
                last_checkpoint_elapsed_nanos INTEGER,
                period_counted INTEGER NOT NULL
            )""".trimIndent()
        )
    }

    private fun normalizePlaylistSequences(db: SupportSQLiteDatabase) {
        db.query("SELECT DISTINCT playlist_id FROM playlist_entries").use { playlists ->
            while (playlists.moveToNext()) {
                val playlistId = playlists.getString(0)
                val entryIds = mutableListOf<String>()
                db.query(
                    "SELECT id FROM playlist_entries WHERE playlist_id = ? ORDER BY sequence, id",
                    arrayOf(playlistId)
                ).use { entries ->
                    while (entries.moveToNext()) entryIds += entries.getString(0)
                }
                entryIds.forEachIndexed { sequence, entryId ->
                    db.execSQL(
                        "UPDATE playlist_entries SET sequence = ? WHERE id = ?",
                        arrayOf<Any>(sequence, entryId)
                    )
                }
            }
        }
    }
}
