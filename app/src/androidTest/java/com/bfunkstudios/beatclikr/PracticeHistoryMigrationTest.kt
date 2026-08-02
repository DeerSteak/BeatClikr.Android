package com.bfunkstudios.beatclikr

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.PracticedSong
import com.bfunkstudios.beatclikr.data.db.BeatClikrDatabase
import com.bfunkstudios.beatclikr.data.db.BeatClikrMigrations
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeHistoryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BeatClikrDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migration4To5PreservesDataAndQualifiesMergedLegacyHistory() {
        helper.createDatabase(DATABASE_NAME, 4).apply {
            execSQL(
                "INSERT INTO songs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(SONG_ID, "Song", "Artist", 120f, 4, "QUARTER_NOTES", null, null, null)
            )
            execSQL(
                "INSERT INTO playlists VALUES (?, ?, ?)",
                arrayOf<Any?>(PLAYLIST_ID, "Set", 1_700_000_000_000L)
            )
            execSQL(
                "INSERT INTO playlist_entries VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(ENTRY_ID, PLAYLIST_ID, SONG_ID, 0)
            )
            insertSession(FIRST_SESSION_ID, 1_700_000_000_000L)
            insertSession(SECOND_SESSION_ID, 1_700_000_000_000L)
            insertPractice(FIRST_PRACTICE_ID, FIRST_SESSION_ID, SONG_ID, 2)
            insertPractice(SECOND_PRACTICE_ID, SECOND_SESSION_ID, SONG_ID, 3)
            insertPractice(METRONOME_PRACTICE_ID, FIRST_SESSION_ID, PracticedSong.METRONOME_SONG_ID, 1)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            5,
            true,
            BeatClikrMigrations.MIGRATION_4_5
        )

        assertEquals(1, db.longValue("SELECT COUNT(*) FROM songs"))
        assertEquals(1, db.longValue("SELECT COUNT(*) FROM playlists"))
        assertEquals(1, db.longValue("SELECT COUNT(*) FROM playlist_entries"))
        assertEquals(1, db.longValue("SELECT COUNT(*) FROM practice_sessions"))
        assertEquals(2, db.longValue("SELECT COUNT(*) FROM practiced_songs"))
        assertEquals(5, db.longValue("SELECT times_practiced FROM practiced_songs WHERE song_id = '$SONG_ID'"))
        assertEquals(60_000_000_000L, db.longValue("SELECT duration_nanos FROM practiced_songs WHERE song_id = '$SONG_ID'"))
        assertEquals(30_000_000_000L, db.longValue("SELECT duration_nanos FROM practiced_songs WHERE song_id = '${PracticedSong.METRONOME_SONG_ID}'"))
        assertEquals("gregorian", db.stringValue("SELECT calendar_identifier FROM practice_sessions"))
        db.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertSession(id: String, date: Long) {
        execSQL("INSERT INTO practice_sessions VALUES (?, ?)", arrayOf<Any?>(id, date))
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertPractice(
        id: String,
        sessionId: String,
        songId: String,
        count: Int
    ) {
        execSQL(
            "INSERT INTO practiced_songs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(id, sessionId, songId, "Artist", null, null, null, count, songId)
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.longValue(query: String): Long =
        this.query(query).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.stringValue(query: String): String =
        this.query(query).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }

    private companion object {
        const val DATABASE_NAME = "phase-5-migration"
        const val SONG_ID = "11111111-1111-1111-1111-111111111111"
        const val PLAYLIST_ID = "22222222-2222-2222-2222-222222222222"
        const val ENTRY_ID = "33333333-3333-3333-3333-333333333333"
        const val FIRST_SESSION_ID = "44444444-4444-4444-4444-444444444444"
        const val SECOND_SESSION_ID = "55555555-5555-5555-5555-555555555555"
        const val FIRST_PRACTICE_ID = "66666666-6666-6666-6666-666666666666"
        const val SECOND_PRACTICE_ID = "77777777-7777-7777-7777-777777777777"
        const val METRONOME_PRACTICE_ID = "88888888-8888-8888-8888-888888888888"
    }
}
