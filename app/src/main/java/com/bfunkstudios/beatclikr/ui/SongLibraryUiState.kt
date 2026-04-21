package com.bfunkstudios.beatclikr.ui

import com.bfunkstudios.beatclikr.data.Song

data class SongLibraryUiState(
    val selectedSong: Song? = null,
    val songList: List<Song> = emptyList()
)
