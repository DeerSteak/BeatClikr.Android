package com.bfunkstudios.beatclikr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SongLibraryViewModel @Inject constructor(
    private val repository: SongRepository
) : ViewModel() {

    private val _selectedSong = MutableStateFlow<Song?>(null)

    val uiState: StateFlow<SongLibraryUiState> = combine(
        repository.getAllSongs(),
        _selectedSong
    ) { songs, selected ->
        SongLibraryUiState(songList = songs, selectedSong = selected)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SongLibraryUiState()
    )

    fun setSelectedSong(uuid: UUID?) {
        _selectedSong.value = if (uuid == null) null
        else uiState.value.songList.find { it.id == uuid }
    }

    fun saveSong(song: Song) {
        viewModelScope.launch { repository.upsert(song) }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch { repository.delete(song) }
    }
}
