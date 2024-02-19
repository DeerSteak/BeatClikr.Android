package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bfunkstudios.beatclikr.data.DataSource
import com.bfunkstudios.beatclikr.data.Song

@Composable
fun SongList(songs: List<Song>, navigateToDetail:() -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(songs) {
                SongListItem(it, navigateToDetail)
            }
        }
    }
}

@Composable
fun SongListItem(song: Song, navigateToDetail: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable {
            navigateToDetail()
        }) {
        Column (modifier = Modifier.fillMaxWidth()) {
            Text(song.title)
            Text(song.artist)
        }
    }
}
