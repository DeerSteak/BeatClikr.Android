package com.bfunkstudios.beatclikr

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bfunkstudios.beatclikr.data.Groove
import com.bfunkstudios.beatclikr.data.Playlist
import com.bfunkstudios.beatclikr.data.PracticeAccountingCheckpoint
import com.bfunkstudios.beatclikr.data.PracticeDayIdentity
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepositoryImpl
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.db.BeatClikrDatabase
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertThrows
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoInstrumentedTest {
    private lateinit var database: BeatClikrDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BeatClikrDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun concurrentAddsAndMutationsKeepDenseUniqueOrdering() = runTest {
        val playlist = Playlist(name = "Set")
        val songs = (0 until 8).map { song("Song $it") }
        database.playlistDao().upsertPlaylist(playlist)
        songs.forEach { database.songDao().upsert(it) }

        songs.map { song ->
            async { database.playlistDao().addEntryAllocated(playlist.id, song.id, 0) }
        }.awaitAll()
        assertDenseOrdering(playlist.id, 8)

        val current = database.playlistDao().getEntriesForMutation(playlist.id)
        database.playlistDao().reorderEntriesDeterministically(current.reversed())
        database.playlistDao().deleteEntryAndResequence(current[3])
        assertDenseOrdering(playlist.id, 7)
    }

    @Test
    fun deletingReferencedSongCascadesItsPlaylistEntry() = runTest {
        val playlist = Playlist(name = "Set")
        val song = song("Referenced")
        database.playlistDao().upsertPlaylist(playlist)
        database.songDao().upsert(song)
        database.playlistDao().addEntryAllocated(playlist.id, song.id, 0)

        database.songDao().delete(song)

        assertEquals(emptyList<Any>(), database.playlistDao().getEntriesForMutation(playlist.id))
    }

    @Test
    fun cancellationRollsBackPlaylistMutation() = runTest {
        val playlist = Playlist(name = "Set")
        val song = song("Cancelled")
        database.playlistDao().upsertPlaylist(playlist)
        database.songDao().upsert(song)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                database.withTransaction {
                    database.playlistDao().addEntryAllocated(playlist.id, song.id, 0)
                    throw CancellationException("cancel mutation")
                }
            }
        }

        assertEquals(emptyList<Any>(), database.playlistDao().getEntriesForMutation(playlist.id))
    }

    @Test
    fun concurrentPlaylistAndPracticeTransactionsPreserveBothInvariants() = runTest {
        val playlist = Playlist(name = "Set")
        val songs = (0 until 4).map { song("Concurrent $it") }
        database.playlistDao().upsertPlaylist(playlist)
        songs.forEach { database.songDao().upsert(it) }
        val practice = PracticeHistoryRepositoryImpl(
            database.practiceHistoryDao(),
            FakePracticeReminderScheduler()
        )
        val day = PracticeDayIdentity.capture(
            Instant.parse("2026-08-02T18:00:00Z"),
            ZoneId.of("America/Chicago")
        )

        val playlistWork = songs.map { song ->
            async { database.playlistDao().addEntryAllocated(playlist.id, song.id, 0) }
        }
        val practiceWork = async {
            practice.applyAccountingUpdate(
                day,
                PracticeItemSnapshot.metronome(),
                30_000_000_000,
                1,
                PracticeAccountingCheckpoint(acknowledgedLifecycleSequence = 1)
            )
        }
        playlistWork.awaitAll()
        practiceWork.await()

        assertDenseOrdering(playlist.id, 4)
        assertEquals(1, database.practiceHistoryDao().getAllSessions().first().size)
    }

    private suspend fun assertDenseOrdering(playlistId: UUID, count: Int) {
        val entries = database.playlistDao().getEntriesForMutation(playlistId)
        assertEquals(count, entries.size)
        assertEquals((0 until count).toList(), entries.map { it.sequence })
        assertEquals(count, entries.map { it.sequence }.toSet().size)
    }

    private fun song(title: String) = Song(
        title = title,
        artist = "Artist",
        beatsPerMinute = 120f,
        beatsPerMeasure = 4,
        groove = Groove.Quarter,
        liveSequence = null,
        rehearsalSequence = null
    )
}
