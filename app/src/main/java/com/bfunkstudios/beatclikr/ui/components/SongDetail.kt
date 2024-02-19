package com.bfunkstudios.beatclikr.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.data.DataSource
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SongListUiState
import com.bfunkstudios.beatclikr.data.Subdivisions
import com.bfunkstudios.beatclikr.ui.SongListViewModel
import java.util.UUID

@Composable
fun SongDetail(uiState: SongListUiState, songlistViewModel: SongListViewModel, navigateBack: () -> Unit) {
    val song = uiState.selectedSong
    Log.d("SongDetail", "Song id: " + song?.id)
    val thisSong = Song(
        song?.title ?: "",
        song?.artist ?: "",
        song?.beatsPerMinute ?: 60f,
        song?.beatsPerMeasure ?: 4,
        song?.subdivisions ?: Subdivisions.Eighth,
        song?.liveSequence,
        song?.rehearsalSequence,
        song?.id ?: UUID.randomUUID()
    )

    var title by remember { mutableStateOf(thisSong.title) }
    var artist by remember { mutableStateOf(thisSong.artist) }
    var beatsPerMinute by remember { mutableFloatStateOf(thisSong.beatsPerMinute)}
    var beatsPerMeasure by remember { mutableIntStateOf(thisSong.beatsPerMeasure) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Title")
        TextField(value = title, onValueChange = {
            title = it
        }, modifier = Modifier.fillMaxWidth())
        Text("Artist")
        TextField(value = artist, onValueChange = {
            artist = it
        }, modifier = Modifier.fillMaxWidth())

        Row {
            Text("Beats Per Minute: ")
            Text(beatsPerMinute.toString())
        }
        Slider(value = beatsPerMinute, onValueChange = { beatsPerMinute = it },
            valueRange = 60f..240f,
            )
        Row {
            Text("Beats Per Measure: ")
            Text(beatsPerMeasure.toString())
        }
        TextField(value = beatsPerMeasure.toString(), onValueChange = {
            beatsPerMeasure = it.toInt()
        }, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = navigateBack, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Spacer(Modifier.weight(0.1f))
            OutlinedButton(onClick = {
                val newSong = Song(
                    title,
                    artist,
                    beatsPerMinute,
                    beatsPerMeasure,
                    thisSong.subdivisions,
                    thisSong.liveSequence,
                    thisSong.rehearsalSequence,
                    thisSong.id
                )
                songlistViewModel.saveSong(newSong)
                navigateBack()
            }, modifier = Modifier.weight(1f)) {
                Text("Save")
            }
        }
    }


}