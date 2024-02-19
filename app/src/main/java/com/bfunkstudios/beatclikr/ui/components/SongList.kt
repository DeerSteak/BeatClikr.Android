package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.DataSource
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SongListUiState
import com.bfunkstudios.beatclikr.ui.SongListViewModel

@Composable
fun SongList(uiState: SongListUiState, viewModel: SongListViewModel, navigateToDetail:() -> Unit = {}) {
    val songs = uiState.songList
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedButton(onClick = {
            viewModel.setSelectedSong(null)
            navigateToDetail()
        }, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(stringResource(id = R.string.add_song))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(songs) {
                SongListItem(it, viewModel, navigateToDetail)
            }
        }
    }
}

@Composable
fun SongListItem(song: Song, songListViewModel: SongListViewModel, navigateToDetail: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable {
            songListViewModel.setSelectedSong(song.id)
            navigateToDetail()
        }) {
        Column (modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(song.title)
            Text(song.artist)
        }
    }
}
