package com.bfunkstudios.beatclikr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.data.Playlist
import com.bfunkstudios.beatclikr.data.PlaylistEntryWithSong
import com.bfunkstudios.beatclikr.data.PlaylistRepository
import com.bfunkstudios.beatclikr.data.PlaylistWithEntries
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SongRepository
import com.bfunkstudios.beatclikr.services.OperationalFailureReporter
import com.bfunkstudios.beatclikr.services.databaseFailure
import com.bfunkstudios.beatclikr.services.launchReporting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val failureReporter: OperationalFailureReporter = OperationalFailureReporter()
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistWithEntries>> = repository.getAllPlaylists()
        .catch { failureReporter.report(databaseFailure("playlist_read")); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlaylistId = MutableStateFlow<UUID?>(null)

    val selectedPlaylist: StateFlow<PlaylistWithEntries?> = _selectedPlaylistId
        .flatMapLatest { id -> id?.let { repository.getPlaylist(it) } ?: flowOf(null) }
        .catch { failureReporter.report(databaseFailure("playlist_read")); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allSongs: StateFlow<List<Song>> = songRepository.getAllSongs()
        .catch { failureReporter.report(databaseFailure("song_read")); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var currentEntryId by mutableStateOf<UUID?>(null)
        private set

    var newPlaylistName by mutableStateOf("")
        private set

    var playlistToRename by mutableStateOf<PlaylistWithEntries?>(null)
        private set

    var renamePlaylistName by mutableStateOf("")
        private set

    // --- List operations ---

    fun selectPlaylist(id: UUID) {
        _selectedPlaylistId.value = id
        currentEntryId = null
    }

    fun updateNewPlaylistName(name: String) {
        newPlaylistName = name
    }

    fun clearNewPlaylistDraft() {
        newPlaylistName = ""
    }

    fun createPlaylistFromDraft(onCreated: (UUID) -> Unit = {}) {
        val name = newPlaylistName
        if (name.isBlank()) return
        createPlaylist(name, onCreated)
        clearNewPlaylistDraft()
    }

    fun createPlaylist(name: String, onCreated: (UUID) -> Unit = {}) {
        launchDatabase("playlist_create") {
            val playlist = repository.createPlaylist(name)
            onCreated(playlist.id)
        }
    }

    fun beginRenamePlaylist(playlist: PlaylistWithEntries) {
        playlistToRename = playlist
        renamePlaylistName = playlist.playlist.name
    }

    fun updateRenamePlaylistName(name: String) {
        renamePlaylistName = name
    }

    fun cancelRenamePlaylist() {
        playlistToRename = null
        renamePlaylistName = ""
    }

    fun confirmRenamePlaylist() {
        val playlist = playlistToRename?.playlist ?: return
        val name = renamePlaylistName
        if (name.isBlank()) return
        renamePlaylist(playlist, name)
        cancelRenamePlaylist()
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        launchDatabase("playlist_rename") { repository.renamePlaylist(playlist, name) }
    }

    fun deletePlaylist(playlistWithEntries: PlaylistWithEntries) {
        launchDatabase("playlist_delete") { repository.deletePlaylist(playlistWithEntries.playlist) }
    }

    // --- Entry operations ---

    fun addSong(songId: UUID) {
        val playlistId = _selectedPlaylistId.value ?: return
        val count = selectedPlaylist.value?.entries?.size ?: 0
        launchDatabase("playlist_add_song") { repository.addEntry(playlistId, songId, count) }
    }

    fun deleteEntry(entry: PlaylistEntryWithSong, entries: List<PlaylistEntryWithSong>) {
        launchDatabase("playlist_delete_song") { repository.deleteEntry(entry.entry) }
    }

    fun reorderEntries(reorderedEntries: List<PlaylistEntryWithSong>) {
        val resequenced = reorderedEntries.mapIndexed { i, e -> e.copy(entry = e.entry.copy(sequence = i)) }
        launchDatabase("playlist_reorder") { repository.reorderEntries(resequenced) }
    }

    // --- Transport ---

    fun sortedEntries(playlist: PlaylistWithEntries?): List<PlaylistEntryWithSong> =
        (playlist?.entries ?: emptyList()).sortedBy { it.entry.sequence }

    fun currentIndex(entries: List<PlaylistEntryWithSong>): Int? =
        currentEntryId?.let { id -> entries.indexOfFirst { it.entry.id == id }.takeIf { it >= 0 } }

    fun currentSongTitle(entries: List<PlaylistEntryWithSong>): String? =
        currentIndex(entries)?.let { entries[it].song.title }

    fun canGoPrevious(entries: List<PlaylistEntryWithSong>): Boolean =
        currentIndex(entries)?.let { it > 0 } ?: false

    fun canGoNext(entries: List<PlaylistEntryWithSong>): Boolean =
        currentIndex(entries)?.let { it < entries.lastIndex } ?: false

    fun playSong(entry: PlaylistEntryWithSong, onPlay: (Song) -> Unit) {
        currentEntryId = entry.entry.id
        onPlay(entry.song)
    }

    fun playOrResume(entries: List<PlaylistEntryWithSong>, onPlay: (Song) -> Unit) {
        val target = currentIndex(entries)?.let { entries[it] } ?: entries.firstOrNull() ?: return
        currentEntryId = target.entry.id
        onPlay(target.song)
    }

    fun playPrevious(entries: List<PlaylistEntryWithSong>, onPlay: (Song) -> Unit) {
        val idx = currentIndex(entries) ?: return
        val entry = entries.getOrNull(idx - 1) ?: return
        currentEntryId = entry.entry.id
        onPlay(entry.song)
    }

    fun playNext(entries: List<PlaylistEntryWithSong>, onPlay: (Song) -> Unit) {
        val idx = currentIndex(entries) ?: return
        val entry = entries.getOrNull(idx + 1) ?: return
        currentEntryId = entry.entry.id
        onPlay(entry.song)
    }

    private fun launchDatabase(code: String, action: suspend () -> Unit) {
        viewModelScope.launchReporting(failureReporter, databaseFailure(code), action)
    }
}
