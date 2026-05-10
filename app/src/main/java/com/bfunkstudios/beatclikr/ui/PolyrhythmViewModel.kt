package com.bfunkstudios.beatclikr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.PolyrhythmAudioEngineDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PolyrhythmViewModel @Inject constructor(
    private val audio: IAudioPlayerService,
    private val prefs: IAppPreferences
) : ViewModel(), PolyrhythmAudioEngineDelegate {

    var beats by mutableIntStateOf(prefs.polyrhythmBeats)
        private set

    var against by mutableIntStateOf(prefs.polyrhythmAgainst)
        private set

    var bpm by mutableFloatStateOf(prefs.polyrhythmBpm)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var selectedBeatSound by mutableStateOf(prefs.polyrhythmBeatSound)
        private set

    var selectedRhythmSound by mutableStateOf(prefs.polyrhythmRhythmSound)
        private set

    var beatPulse by mutableFloatStateOf(0f)
        private set

    var rhythmPulse by mutableFloatStateOf(0f)
        private set

    var activeBeatIndex by mutableIntStateOf(0)
        private set

    var activeRhythmIndex by mutableIntStateOf(0)
        private set

    var playheadResetID by mutableIntStateOf(0)
        private set

    val cycleDurationMillis: Int
        get() = (against * (60_000f / bpm)).toInt().coerceAtLeast(1)

    private var beatPulseJob: Job? = null
    private var rhythmPulseJob: Job? = null

    init {
        audio.polyrhythmDelegate = this
    }

    fun updateBeats(value: Int) {
        beats = value.coerceIn(1, 15)
        prefs.polyrhythmBeats = beats
        if (isPlaying) start()
    }

    fun updateAgainst(value: Int) {
        against = value.coerceIn(1, 15)
        prefs.polyrhythmAgainst = against
        if (isPlaying) start()
    }

    fun updateBpm(value: Float) {
        bpm = value.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
        prefs.polyrhythmBpm = bpm
        if (isPlaying) start()
    }

    fun updateBeatSound(sound: SoundFile) {
        selectedBeatSound = sound
        prefs.polyrhythmBeatSound = sound
        setupPolyrhythm()
    }

    fun updateRhythmSound(sound: SoundFile) {
        selectedRhythmSound = sound
        prefs.polyrhythmRhythmSound = sound
        setupPolyrhythm()
    }

    fun setupPolyrhythm() {
        val beatResId = selectedBeatSound.resourceId
        val rhythmResId = selectedRhythmSound.resourceId
        if (beatResId != null && rhythmResId != null) {
            audio.setupAudioPlayer(beatResId, rhythmResId)
        }
    }

    fun togglePlayPause() {
        if (isPlaying) stop() else start()
    }

    fun start() {
        setupPolyrhythm()
        playheadResetID += 1
        beatPulse = 0f
        rhythmPulse = 0f
        audio.isMuted = prefs.muteMetronome
        audio.startPolyrhythm(bpm, beats, against)
        isPlaying = true
    }

    fun stop() {
        audio.stopPolyrhythm()
        isPlaying = false
        beatPulseJob?.cancel()
        rhythmPulseJob?.cancel()
        beatPulse = 0f
        rhythmPulse = 0f
        playheadResetID += 1
    }

    override fun polyrhythmBeatFired(
        beatFired: Boolean,
        rhythmFired: Boolean,
        beatIndex: Int,
        rhythmIndex: Int
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            val quarterDurationMillis = (60_000f / bpm).toLong().coerceAtLeast(1L)

            if (beatFired) {
                activeBeatIndex = beatIndex
                beatPulseJob?.cancel()
                beatPulseJob = launch {
                    fadePulse(
                        durationMillis = quarterDurationMillis,
                        onPulseChanged = { beatPulse = it }
                    )
                }
                if (beatIndex == 0) {
                    playheadResetID += 1
                }
            }

            if (rhythmFired) {
                activeRhythmIndex = rhythmIndex
                val rhythmDurationMillis = (against * (60_000f / bpm) / beats)
                    .toLong()
                    .coerceAtLeast(1L)
                rhythmPulseJob?.cancel()
                rhythmPulseJob = launch {
                    fadePulse(
                        durationMillis = rhythmDurationMillis,
                        onPulseChanged = { rhythmPulse = it }
                    )
                }
            }
        }
    }

    private suspend fun fadePulse(
        durationMillis: Long,
        onPulseChanged: (Float) -> Unit
    ) {
        onPulseChanged(1f)
        val startedAt = System.nanoTime()
        val durationNanos = durationMillis * 1_000_000L

        while (viewModelScope.isActive) {
            val elapsed = System.nanoTime() - startedAt
            if (elapsed >= durationNanos) break

            val progress = elapsed.toFloat() / durationNanos.toFloat()
            onPulseChanged(1f - progress.coerceIn(0f, 1f))
            delay(16L)
        }
        onPulseChanged(0f)
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        audio.polyrhythmDelegate = null
    }
}
