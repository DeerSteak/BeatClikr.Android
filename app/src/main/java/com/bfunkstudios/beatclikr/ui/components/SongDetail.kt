package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.constants.AppLocale
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.ui.SongLibraryViewModel

@Composable
fun SongDetail(
    viewModel: SongLibraryViewModel,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel(stringResource(R.string.section_song_info))
        SectionCard {
            FormRow(label = stringResource(R.string.song_title)) {
                InlineTextField(
                    value = viewModel.draftTitle,
                    onValueChange = { viewModel.updateDraftTitle(it) },
                    placeholder = stringResource(R.string.required),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            FormRow(label = stringResource(R.string.artist)) {
                InlineTextField(
                    value = viewModel.draftArtist,
                    onValueChange = { viewModel.updateDraftArtist(it) },
                    placeholder = stringResource(R.string.required),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
            }
        }

        SectionLabel(stringResource(R.string.section_tempo))
        SectionCard {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.bpm), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = String.format(AppLocale, "%.0f", viewModel.draftBpm),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Thin
                    )
                }
                BpmSliderControl(
                    value = viewModel.draftBpm,
                    onValueChange = { viewModel.updateDraftBpm(it) },
                    valueRange = MetronomeConstants.MIN_BPM..MetronomeConstants.MAX_BPM
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 12.dp))
            FormRow(label = stringResource(R.string.beats_per_bar)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedIconButton(
                        onClick = { viewModel.updateDraftBeatsPerMeasure(viewModel.draftBeatsPerMeasure - 1) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text("−", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = viewModel.draftBeatsPerMeasure.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    OutlinedIconButton(
                        onClick = { viewModel.updateDraftBeatsPerMeasure(viewModel.draftBeatsPerMeasure + 1) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.groove))
        SectionCard {
            GrooveSelector(
                selected = viewModel.draftGroove,
                onSelect = { viewModel.updateDraftGroove(it) },
                modifier = Modifier.padding(12.dp)
            )
            if (viewModel.draftGroove.isOddMeter) {
                HorizontalDivider(modifier = Modifier.padding(start = 12.dp))
                BeatPatternSelector(
                    selected = viewModel.draftBeatPattern,
                    onSelect = { viewModel.updateDraftBeatPattern(it) },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun FormRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface
        ),
        keyboardOptions = keyboardOptions,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}
