package com.bfunkstudios.beatclikr.data

data class SongListUiState (
    val selectedSong: Song? = null,
    val songList: List<Song> = DataSource.songs
)