package com.bfunkstudios.beatclikr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundFile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: IAppPreferences
) : ViewModel() {

    var useFlashlight by mutableStateOf(prefs.useFlashlight)
        private set

    var useVibration by mutableStateOf(prefs.useVibration)
        private set

    var alwaysUseDarkTheme by mutableStateOf(prefs.alwaysUseDarkTheme)
        private set

    var muteMetronome by mutableStateOf(prefs.muteMetronome)
        private set

    var keepScreenAwake by mutableStateOf(prefs.keepScreenAwake)
        private set

    var sixteenthAlternate by mutableStateOf(prefs.sixteenthAlternate)
        private set

    var metronomeBeatSound by mutableStateOf(prefs.instantBeatSound)
        private set

    var metronomeRhythmSound by mutableStateOf(prefs.instantRhythmSound)
        private set

    var playlistBeatSound by mutableStateOf(prefs.playlistBeatSound)
        private set

    var playlistRhythmSound by mutableStateOf(prefs.playlistRhythmSound)
        private set

    var polyrhythmBeatSound by mutableStateOf(prefs.polyrhythmBeatSound)
        private set

    var polyrhythmRhythmSound by mutableStateOf(prefs.polyrhythmRhythmSound)
        private set

    fun updateUseFlashlight(value: Boolean) {
        useFlashlight = value
        prefs.useFlashlight = value
    }

    fun updateUseVibration(value: Boolean) {
        useVibration = value
        prefs.useVibration = value
    }

    fun updateAlwaysUseDarkTheme(value: Boolean) {
        alwaysUseDarkTheme = value
        prefs.alwaysUseDarkTheme = value
    }

    fun updateMuteMetronome(value: Boolean) {
        muteMetronome = value
        prefs.muteMetronome = value
    }

    fun updateKeepScreenAwake(value: Boolean) {
        keepScreenAwake = value
        prefs.keepScreenAwake = value
    }

    fun updateSixteenthAlternate(value: Boolean) {
        sixteenthAlternate = value
        prefs.sixteenthAlternate = value
    }

    fun updateMetronomeBeatSound(value: SoundFile) {
        metronomeBeatSound = value
        prefs.instantBeatSound = value
    }

    fun updateMetronomeRhythmSound(value: SoundFile) {
        metronomeRhythmSound = value
        prefs.instantRhythmSound = value
    }

    fun updatePlaylistBeatSound(value: SoundFile) {
        playlistBeatSound = value
        prefs.playlistBeatSound = value
    }

    fun updatePlaylistRhythmSound(value: SoundFile) {
        playlistRhythmSound = value
        prefs.playlistRhythmSound = value
    }

    fun updatePolyrhythmBeatSound(value: SoundFile) {
        polyrhythmBeatSound = value
        prefs.polyrhythmBeatSound = value
    }

    fun updatePolyrhythmRhythmSound(value: SoundFile) {
        polyrhythmRhythmSound = value
        prefs.polyrhythmRhythmSound = value
    }
}
