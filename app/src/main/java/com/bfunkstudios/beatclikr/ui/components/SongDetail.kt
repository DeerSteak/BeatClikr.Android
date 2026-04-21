package com.bfunkstudios.beatclikr.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SongLibraryUiState
import com.bfunkstudios.beatclikr.data.Subdivisions
import com.bfunkstudios.beatclikr.ui.SongListViewModel
import java.util.UUID

@Composable
fun SongDetail(uiState: SongLibraryUiState, songlistViewModel: SongListViewModel, navigateBack: () -> Unit) {
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
        Text(stringResource(R.string.song_title))
        TextField(value = title, onValueChange = {
            title = it
        }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.artist))
        TextField(value = artist, onValueChange = {
            artist = it
        }, modifier = Modifier.fillMaxWidth())

        Text(stringResource(R.string.beats_per_minute_label, beatsPerMinute.toInt()))
        Slider(
            value = beatsPerMinute,
            onValueChange = { beatsPerMinute = it },
            valueRange = 60f..240f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary,
                inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
            )
        )
        Text(stringResource(R.string.beats_per_measure_label, beatsPerMeasure))
        TextField(value = beatsPerMeasure.toString(), onValueChange = {
            beatsPerMeasure = it.toInt()
        }, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = navigateBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
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
                Text(stringResource(R.string.save))
            }
        }
    }


}