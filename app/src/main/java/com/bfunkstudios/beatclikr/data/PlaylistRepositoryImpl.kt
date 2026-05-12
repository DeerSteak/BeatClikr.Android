package com.bfunkstudios.beatclikr.data

import com.bfunkstudios.beatclikr.data.db.PlaylistDao
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class PlaylistRepositoryImpl @Inject constructor(
    private val dao: PlaylistDao
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<PlaylistWithEntries>> = dao.getAllPlaylists()

    override fun getPlaylist(id: UUID): Flow<PlaylistWithEntries?> = dao.getPlaylist(id)

    override suspend fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(name = name.trim())
        dao.upsertPlaylist(playlist)
        return playlist
    }

    override suspend fun renamePlaylist(playlist: Playlist, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) dao.upsertPlaylist(playlist.copy(name = trimmed))
    }

    override suspend fun deletePlaylist(playlist: Playlist) = dao.deletePlaylist(playlist)

    override suspend fun addEntry(playlistId: UUID, songId: UUID, sequence: Int) {
        dao.upsertEntry(PlaylistEntry(playlistId = playlistId, songId = songId, sequence = sequence))
    }

    override suspend fun deleteEntry(entry: PlaylistEntry) = dao.deleteEntry(entry)

    override suspend fun reorderEntries(entries: List<PlaylistEntryWithSong>) {
        dao.updateEntries(entries.map { it.entry })
    }
}
