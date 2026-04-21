package com.bfunkstudios.beatclikr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.ClickerType
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.data.Subdivisions
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MetronomeViewModel @Inject constructor(
    private val audio: IAudioPlayerService,
    private val prefs: IAppPreferences
) : ViewModel(), MetronomeAudioEngineDelegate {

    var iconScale by mutableFloatStateOf(MetronomeConstants.ICON_SCALE_MIN)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var clickerType by mutableStateOf(ClickerType.INSTANT)
        private set

    var currentSong by mutableStateOf(
        Song.instantSong().copy(
            beatsPerMinute = prefs.instantBpm,
            subdivisions = prefs.instantSubdivisions
        )
    )
        private set

    val beatsPerMinute: Float get() = currentSong.beatsPerMinute
    val selectedSubdivisions: Subdivisions get() = currentSong.subdivisions

    var selectedBeatSound by mutableStateOf(prefs.instantBeatSound)
        private set

    var selectedRhythmSound by mutableStateOf(prefs.instantRhythmSound)
        private set

    private val tapTimestamps = mutableListOf<Long>()

    init {
        audio.delegate = this
    }

    fun loadSong(song: Song, type: ClickerType = ClickerType.INSTANT) {
        currentSong = song
        clickerType = type
        if (isPlaying) {
            audio.updateTempo(currentSong.beatsPerMinute, getSubdivisionValue())
        }
    }

    fun updateBPM(bpm: Float) {
        currentSong = currentSong.copy(
            beatsPerMinute = bpm.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
        )
        if (clickerType == ClickerType.INSTANT) prefs.instantBpm = currentSong.beatsPerMinute
        if (isPlaying) audio.updateTempo(currentSong.beatsPerMinute, getSubdivisionValue())
    }

    fun updateSubdivisions(subdivisions: Subdivisions) {
        currentSong = currentSong.copy(subdivisions = subdivisions)
        if (clickerType == ClickerType.INSTANT) prefs.instantSubdivisions = currentSong.subdivisions
        if (isPlaying) audio.updateTempo(currentSong.beatsPerMinute, getSubdivisionValue())
    }

    fun updateBeatSound(sound: SoundFile) {
        selectedBeatSound = sound
        if (clickerType == ClickerType.INSTANT) prefs.instantBeatSound = sound
        val beatResId = sound.resourceId
        val rhythmResId = selectedRhythmSound.resourceId
        if (beatResId != null && rhythmResId != null) setupMetronome(beatResId, rhythmResId)
    }

    fun updateRhythmSound(sound: SoundFile) {
        selectedRhythmSound = sound
        if (clickerType == ClickerType.INSTANT) prefs.instantRhythmSound = sound
        val beatResId = selectedBeatSound.resourceId
        val rhythmResId = sound.resourceId
        if (beatResId != null && rhythmResId != null) setupMetronome(beatResId, rhythmResId)
    }

    fun setupMetronome(beatResourceId: Int, rhythmResourceId: Int) {
        audio.setupAudioPlayer(beatResourceId, rhythmResourceId)
    }

    fun togglePlayPause() {
        if (isPlaying) stop() else start()
    }

    fun start() {
        if (clickerType == ClickerType.INSTANT) {
            currentSong = Song.instantSong().copy(
                beatsPerMinute = currentSong.beatsPerMinute,
                subdivisions = currentSong.subdivisions
            )
        }
        audio.isMuted = prefs.muteMetronome
        audio.startMetronome(currentSong.beatsPerMinute, getSubdivisionValue())
        isPlaying = true
    }

    fun stop() {
        audio.stopMetronome()
        isPlaying = false
        iconScale = MetronomeConstants.ICON_SCALE_MIN
    }

    fun recordTap() {
        val now = System.currentTimeMillis()

        if (tapTimestamps.isNotEmpty() && now - tapTimestamps.last() > 2000) {
            tapTimestamps.clear()
        }

        tapTimestamps.add(now)
        if (tapTimestamps.size > 8) tapTimestamps.removeAt(0)
        if (tapTimestamps.size < 2) return

        val avgIntervalMs = (0 until tapTimestamps.size - 1)
            .map { tapTimestamps[it + 1] - tapTimestamps[it] }
            .average()

        updateBPM((60_000.0 / avgIntervalMs).toFloat())
    }

    override fun metronomeBeatFired(isBeat: Boolean) {
        if (isBeat) {
            iconScale = MetronomeConstants.ICON_SCALE_MAX
            viewModelScope.launch {
                delay(16)
                iconScale = MetronomeConstants.ICON_SCALE_MIN
            }
            handleBeat()
        } else {
            handleRhythm()
        }
    }

    private fun handleBeat() {}

    private fun handleRhythm() {}

    private fun getSubdivisionValue(): Int = when (currentSong.subdivisions) {
        Subdivisions.Quarter -> 1
        Subdivisions.Eighth -> 2
        Subdivisions.Triplet -> 3
        Subdivisions.Sixteenth -> 4
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        audio.delegate = null
    }
}
