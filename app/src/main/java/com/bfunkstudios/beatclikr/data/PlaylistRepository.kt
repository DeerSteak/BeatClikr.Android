package com.bfunkstudios.beatclikr.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<PlaylistWithEntries>>
    fun getPlaylist(id: UUID): Flow<PlaylistWithEntries?>
    suspend fun createPlaylist(name: String): Playlist
    suspend fun renamePlaylist(playlist: Playlist, name: String)
    suspend fun deletePlaylist(playlist: Playlist)
    suspend fun addEntry(playlistId: UUID, songId: UUID, sequence: Int)
    suspend fun deleteEntry(entry: PlaylistEntry)
    suspend fun reorderEntries(entries: List<PlaylistEntryWithSong>)
}
