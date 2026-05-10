@file:OptIn(ExperimentalMaterial3Api::class)

package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bfunkstudios.beatclikr.R

private enum class MetronomeMode {
    Metronome,
    Polyrhythm
}

@Composable
fun MetronomeContainerView(
    metronomeViewModel: MetronomeViewModel,
    modifier: Modifier = Modifier,
    polyrhythmViewModel: PolyrhythmViewModel = hiltViewModel()
) {
    var selectedMode by remember { mutableStateOf(MetronomeMode.Metronome) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.width(240.dp)) {
                MetronomeMode.entries.forEachIndexed { index, mode ->
                    val selected = selectedMode == mode
                    SegmentedButton(
                        selected = selected,
                        modifier = Modifier.testTag(
                            when (mode) {
                                MetronomeMode.Metronome -> "metronome_mode_metronome"
                                MetronomeMode.Polyrhythm -> "metronome_mode_polyrhythm"
                            }
                        ),
                        onClick = {
                            if (selectedMode != mode) {
                                when (mode) {
                                    MetronomeMode.Metronome -> polyrhythmViewModel.stop()
                                    MetronomeMode.Polyrhythm -> metronomeViewModel.stop()
                                }
                                selectedMode = mode
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = MetronomeMode.entries.size
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.primary,
                            inactiveBorderColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = when (mode) {
                                MetronomeMode.Metronome -> stringResource(R.string.instant_metronome)
                                MetronomeMode.Polyrhythm -> stringResource(R.string.polyrhythm)
                            }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedMode) {
                MetronomeMode.Metronome -> MetronomeView(viewModel = metronomeViewModel)
                MetronomeMode.Polyrhythm -> PolyrhythmView(viewModel = polyrhythmViewModel)
            }
        }
    }
}
