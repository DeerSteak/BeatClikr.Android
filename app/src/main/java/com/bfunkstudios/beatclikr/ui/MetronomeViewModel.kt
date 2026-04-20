package com.bfunkstudios.beatclikr.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.data.Subdivisions
import com.bfunkstudios.beatclikr.services.AudioPlayerService
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel for metronome playback control.
 * Matches iOS MetronomePlaybackViewModel architecture.
 */
class MetronomeViewModel(application: Application) : AndroidViewModel(application),
    MetronomeAudioEngineDelegate {

    private val audio: AudioPlayerService = AudioPlayerService.getInstance(application)

    // Published state
    var iconScale by mutableFloatStateOf(MetronomeConstants.ICON_SCALE_MIN)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var beatsPerMinute by mutableFloatStateOf(120f)
        private set

    var selectedSubdivisions by mutableStateOf(Subdivisions.Quarter)
        private set

    var selectedBeatSound by mutableStateOf(SoundFile.CLICK_HI)
        private set

    var selectedRhythmSound by mutableStateOf(SoundFile.CLICK_LO)
        private set

    private val tapTimestamps = mutableListOf<Long>()

    init {
        audio.delegate = this
    }

    fun updateBPM(bpm: Float) {
        var clampedBpm = bpm.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
        beatsPerMinute = clampedBpm

        if (isPlaying) {
            audio.updateTempo(beatsPerMinute, getSubdivisionValue())
        }
    }

    fun updateSubdivisions(subdivisions: Subdivisions) {
        selectedSubdivisions = subdivisions

        if (isPlaying) {
            audio.updateTempo(beatsPerMinute, getSubdivisionValue())
        }
    }

    fun updateBeatSound(sound: SoundFile) {
        selectedBeatSound = sound
        // Reload audio with new sounds
        val beatResId = sound.resourceId
        val rhythmResId = selectedRhythmSound.resourceId
        if (beatResId != null && rhythmResId != null) {
            setupMetronome(beatResId, rhythmResId)
        }
    }

    fun updateRhythmSound(sound: SoundFile) {
        selectedRhythmSound = sound
        // Reload audio with new sounds
        val beatResId = selectedBeatSound.resourceId
        val rhythmResId = sound.resourceId
        if (beatResId != null && rhythmResId != null) {
            setupMetronome(beatResId, rhythmResId)
        }
    }

    fun setupMetronome(beatResourceId: Int, rhythmResourceId: Int) {
        audio.setupAudioPlayer(beatResourceId, rhythmResourceId)
    }

    fun togglePlayPause() {
        if (isPlaying) {
            stop()
        } else {
            start()
        }
    }

    fun start() {
        audio.startMetronome(beatsPerMinute, getSubdivisionValue())
        isPlaying = true
    }

    fun stop() {
        audio.stopMetronome()
        isPlaying = false
        iconScale = MetronomeConstants.ICON_SCALE_MIN
    }

    fun recordTap() {
        val now = System.currentTimeMillis()

        // Reset if last tap was more than 2 seconds ago
        if (tapTimestamps.isNotEmpty()) {
            val lastTap = tapTimestamps.last()
            if (now - lastTap > 2000) {
                tapTimestamps.clear()
            }
        }

        tapTimestamps.add(now)

        // Keep only last 8 taps
        if (tapTimestamps.size > 8) {
            tapTimestamps.removeAt(0)
        }

        // Need at least 2 taps to calculate BPM
        if (tapTimestamps.size < 2) return

        // Calculate average interval between taps
        val intervals = mutableListOf<Long>()
        for (i in 0 until tapTimestamps.size - 1) {
            intervals.add(tapTimestamps[i + 1] - tapTimestamps[i])
        }

        val avgIntervalMs = intervals.average()
        val bpm = 60_000.0 / avgIntervalMs

        // Update BPM with bounds checking
        val roundedBpm = bpm.toFloat()
        updateBPM(roundedBpm.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM))
    }

    override fun metronomeBeatFired(isBeat: Boolean) {
        if (isBeat) {
            // Reset scale to max instantly on beat
            iconScale = MetronomeConstants.ICON_SCALE_MAX
            android.util.Log.d("MetronomeViewModel", "Beat fired! Scale set to MAX: $iconScale")

            // Use coroutine to delay setting back to min so animation can happen
            viewModelScope.launch {
                delay(16) // One frame delay to ensure MAX state is observed
                iconScale = MetronomeConstants.ICON_SCALE_MIN
                android.util.Log.d("MetronomeViewModel", "Scale set to MIN: $iconScale, BPM: $beatsPerMinute")
            }

            handleBeat()
        } else {
            handleRhythm()
        }
    }

    private fun handleBeat() {
        // Beat visual/haptic feedback goes here
        // Audio is handled by the audio engine
    }

    private fun handleRhythm() {
        // Rhythm visual/haptic feedback goes here
        // Audio is handled by the audio engine
    }

    private fun getSubdivisionValue(): Int {
        return when (selectedSubdivisions) {
            Subdivisions.Quarter -> 1
            Subdivisions.Eighth -> 2
            Subdivisions.Triplet -> 3
            Subdivisions.Sixteenth -> 4
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        audio.release()
    }
}
