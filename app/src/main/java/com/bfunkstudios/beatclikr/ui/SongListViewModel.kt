package com.bfunkstudios.beatclikr.ui

import androidx.lifecycle.ViewModel
import com.bfunkstudios.beatclikr.data.DataSource
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SongListUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class SongListViewModel: ViewModel() {
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    fun setSelectedSong(uuid: UUID?) {
        if (uuid == null) {
            _uiState.update { currentState ->
                currentState.copy(
                    selectedSong = null
                )
            }
        } else {
            val newSelection = DataSource.songs.find { it.id == uuid }
            _uiState.update { currentState ->
                currentState.copy(
                    selectedSong = newSelection
                )
            }
        }
    }

    fun saveSong(song: Song) {
        DataSource.saveSong(song)
        _uiState.update { currentState ->
            currentState.copy(
                songList = DataSource.songs
            )
        }
    }
}