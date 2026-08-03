package com.bfunkstudios.beatclikr.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.bfunkstudios.beatclikr.data.Playlist
import com.bfunkstudios.beatclikr.data.PlaylistEntry
import com.bfunkstudios.beatclikr.data.PlaylistWithEntries
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistWithEntries>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylist(playlistId: UUID): Flow<PlaylistWithEntries?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<PlaylistEntry>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsertEntry(entry: PlaylistEntry)

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY sequence, id")
    suspend fun getEntriesForMutation(playlistId: UUID): List<PlaylistEntry>

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun deleteEntriesForPlaylist(playlistId: UUID)

    @Transaction
    suspend fun addEntryAllocated(playlistId: UUID, songId: UUID, requestedSequence: Int) {
        val current = getEntriesForMutation(playlistId)
        val target = requestedSequence.coerceIn(0, current.size)
        val inserted = PlaylistEntry(playlistId = playlistId, songId = songId, sequence = target)
        replaceOrdered(playlistId, current.toMutableList().apply { add(target, inserted) })
    }

    @Transaction
    suspend fun deleteEntryAndResequence(entry: PlaylistEntry) {
        val current = getEntriesForMutation(entry.playlistId)
        require(current.any { it.id == entry.id }) { "Playlist entry does not exist" }
        replaceOrdered(entry.playlistId, current.filterNot { it.id == entry.id })
    }

    @Transaction
    suspend fun reorderEntriesDeterministically(entries: List<PlaylistEntry>) {
        if (entries.isEmpty()) return
        val playlistId = entries.first().playlistId
        require(entries.all { it.playlistId == playlistId }) { "Entries span playlists" }
        val current = getEntriesForMutation(playlistId)
        require(entries.map { it.id }.toSet().size == entries.size) { "Duplicate entry id" }
        require(entries.map { it.id }.toSet() == current.map { it.id }.toSet()) {
            "Reorder must contain every current entry exactly once"
        }
        val currentById = current.associateBy { it.id }
        replaceOrdered(playlistId, entries.map { requireNotNull(currentById[it.id]) })
    }

    private suspend fun replaceOrdered(playlistId: UUID, entries: List<PlaylistEntry>) {
        deleteEntriesForPlaylist(playlistId)
        if (entries.isNotEmpty()) {
            insertEntries(entries.mapIndexed { sequence, entry -> entry.copy(sequence = sequence) })
        }
    }
}
