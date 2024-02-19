package com.bfunkstudios.beatclikr.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.modifierLocalConsumer
import com.bfunkstudios.beatclikr.models.Song
import com.bfunkstudios.beatclikr.models.Subdivisions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScaffold() {
    var songs by remember { mutableStateOf(listOf<Song>()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Song Library")
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newSong = Song(
                    "Jump",
                    "Van Halen",
                    129,
                    4,
                    Subdivisions.EIGHTH,
                    null,
                    null
                )
                songs = songs + newSong
            }) {
                Icon(Icons.Filled.Add, "Add item")
            }
        }

    ) { innerPadding ->
        Column (modifier = Modifier.padding(innerPadding)) {
            SongList(songs)
        }
    }
}

@Composable
fun SongList(songs: List<Song>) {
    Column(modifier = Modifier.fillMaxSize()) {

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(songs) {
                Text(it.title)
                Text(it.artist)
            }
        }
    }
}
