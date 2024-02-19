package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.data.DataSource
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SongListUiState
import com.bfunkstudios.beatclikr.data.Subdivisions

@Composable
fun SongDetail(uiState: SongListUiState, navigateBack: () -> Unit) {
    val song = uiState.selectedSong
    val thisSong = Song(
        song?.artist ?: "",
        song?.title ?: "",
        song?.beatsPerMinute ?: 60f,
        song?.beatsPerMeasure ?: 4,
        song?.subdivisions ?: Subdivisions.Eighth,
        song?.liveSequence,
        song?.rehearsalSequence
    )

    val title = thisSong.title
    val artist = thisSong.artist
    val beatsPerMinute = thisSong.beatsPerMinute
    val beatsPerMeasure = thisSong.beatsPerMeasure

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Title")
        TextField(value = title, onValueChange = {
            thisSong.title = it
        })
        Text("Artist")
        TextField(value = artist, onValueChange = {
            thisSong.artist = it
        })
        Text("Beats Per Minute")
        Slider(value = beatsPerMinute, onValueChange = {
            thisSong.beatsPerMinute = it
        })
        Text("Beats Per Measure")
        TextField(value = beatsPerMeasure.toString(), onValueChange = {
            thisSong.beatsPerMeasure = it.toInt()
        })
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = navigateBack) {
                Text("Cancel")
            }
            OutlinedButton(onClick = {
                val newSong = Song(title, artist, beatsPerMinute, beatsPerMeasure, Subdivisions.Quarter, null, null)
                DataSource.saveSong(newSong)
                navigateBack()
            }) {
                Text("Save")
            }
        }
    }


}