package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.ui.components.SectionCard
import com.bfunkstudios.beatclikr.ui.components.SoundPickerRow

@Composable
fun SettingsView(
    metronomeViewModel: MetronomeViewModel,
    modifier: Modifier = Modifier,
    onAlwaysUseDarkThemeChange: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SettingsSectionTitle(stringResource(R.string.appearance))
        SectionCard {
            SettingsToggleRow(
                label = stringResource(R.string.settings_always_use_dark_theme),
                checked = viewModel.alwaysUseDarkTheme,
                switchModifier = Modifier.testTag("always_use_dark_theme_switch"),
                onCheckedChange = {
                    viewModel.updateAlwaysUseDarkTheme(it)
                    onAlwaysUseDarkThemeChange(it)
                }
            )
        }

        SettingsSectionTitle(stringResource(R.string.settings_metronome_playback))
        SectionCard {
            SettingsToggleRow(
                label = stringResource(R.string.settings_flashlight),
                checked = viewModel.useFlashlight,
                onCheckedChange = {
                    viewModel.updateUseFlashlight(it)
                    metronomeViewModel.refreshPlaybackSettings()
                }
            )
            SettingsDivider()
            SettingsToggleRow(
                label = stringResource(R.string.settings_vibration),
                checked = viewModel.useVibration,
                onCheckedChange = {
                    viewModel.updateUseVibration(it)
                    metronomeViewModel.refreshPlaybackSettings()
                }
            )
            SettingsDivider()
            SettingsToggleRow(
                label = stringResource(R.string.settings_mute_metronome),
                checked = viewModel.muteMetronome,
                onCheckedChange = {
                    viewModel.updateMuteMetronome(it)
                    metronomeViewModel.refreshPlaybackSettings()
                }
            )
            SettingsDivider()
            SettingsToggleRow(
                label = stringResource(R.string.settings_keep_awake),
                checked = viewModel.keepScreenAwake,
                onCheckedChange = { viewModel.updateKeepScreenAwake(it) }
            )
            SettingsDivider()
            SettingsToggleRow(
                label = stringResource(R.string.settings_sixteenth_alternate),
                checked = viewModel.sixteenthAlternate,
                onCheckedChange = {
                    viewModel.updateSixteenthAlternate(it)
                    metronomeViewModel.refreshPlaybackSettings()
                }
            )
        }
        SettingsFooter(stringResource(R.string.settings_metronome_playback_description))

        SettingsSectionTitle(stringResource(R.string.settings_metronome_instruments))
        SectionCard {
            SoundPickerRow(
                label = stringResource(R.string.beat),
                selected = viewModel.metronomeBeatSound,
                options = SoundFile.beatSounds,
                onSelect = {
                    viewModel.updateMetronomeBeatSound(it)
                    metronomeViewModel.applyMetronomeSoundSettings(it, viewModel.metronomeRhythmSound)
                }
            )
            SettingsDivider()
            SoundPickerRow(
                label = stringResource(R.string.rhythm),
                selected = viewModel.metronomeRhythmSound,
                options = SoundFile.rhythmSounds,
                onSelect = {
                    viewModel.updateMetronomeRhythmSound(it)
                    metronomeViewModel.applyMetronomeSoundSettings(viewModel.metronomeBeatSound, it)
                }
            )
        }

        SettingsSectionTitle(stringResource(R.string.settings_playlist_instruments))
        SectionCard {
            SoundPickerRow(
                label = stringResource(R.string.beat),
                selected = viewModel.playlistBeatSound,
                options = SoundFile.beatSounds,
                onSelect = { viewModel.updatePlaylistBeatSound(it) }
            )
            SettingsDivider()
            SoundPickerRow(
                label = stringResource(R.string.rhythm),
                selected = viewModel.playlistRhythmSound,
                options = SoundFile.rhythmSounds,
                onSelect = { viewModel.updatePlaylistRhythmSound(it) }
            )
        }
        SettingsFooter(stringResource(R.string.settings_instruments_description))

        SettingsSectionTitle(stringResource(R.string.settings_polyrhythm_instruments))
        SectionCard {
            SoundPickerRow(
                label = stringResource(R.string.beat),
                selected = viewModel.polyrhythmBeatSound,
                options = SoundFile.beatSounds,
                onSelect = { viewModel.updatePolyrhythmBeatSound(it) }
            )
            SettingsDivider()
            SoundPickerRow(
                label = stringResource(R.string.rhythm),
                selected = viewModel.polyrhythmRhythmSound,
                options = SoundFile.rhythmSounds,
                onSelect = { viewModel.updatePolyrhythmRhythmSound(it) }
            )
        }

        SettingsSectionTitle(stringResource(R.string.about))
        SectionCard {
            SettingsValueRow(
                label = stringResource(R.string.version),
                value = stringResource(R.string.version_value)
            )
        }
        Spacer(modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchModifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier
        )
    }
}

@Composable
private fun SettingsValueRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 12.dp))
}
