package com.bfunkstudios.beatclikr.ui

import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepository
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.PolyrhythmAudioEngineDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PolyrhythmViewModel @Inject constructor(
    private val audio: IAudioPlayerService,
    private val prefs: IAppPreferences,
    private val practiceHistory: PracticeHistoryRepository
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

    private var choreographer: Choreographer? = null
    private var choreographerCallback: Choreographer.FrameCallback? = null
    private var lastBeatTimeNanos: Long = 0L
    private var currentBeatDurationNanos: Long = 0L
    private var lastRhythmTimeNanos: Long = 0L
    private var currentRhythmDurationNanos: Long = 0L

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) {
            if (isPlaying) stop()
        }
    }

    init {
        audio.polyrhythmDelegate = this
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
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
        viewModelScope.launch { practiceHistory.recordPolyrhythmPractice() }
    }

    fun stop() {
        audio.stopPolyrhythm()
        isPlaying = false
        stopChoreographerLoop()
        beatPulse = 0f
        rhythmPulse = 0f
        lastBeatTimeNanos = 0L
        currentBeatDurationNanos = 0L
        lastRhythmTimeNanos = 0L
        currentRhythmDurationNanos = 0L
        playheadResetID += 1
    }

    override fun polyrhythmBeatFired(
        beatFired: Boolean,
        rhythmFired: Boolean,
        beatIndex: Int,
        rhythmIndex: Int,
        stepTimeNanos: Long,
        beatDurationNanos: Long,
        rhythmDurationNanos: Long
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            if (beatFired) {
                activeBeatIndex = beatIndex
                // Show the hit immediately; Choreographer owns the frame-synced decay.
                beatPulse = 1f
                if (stepTimeNanos > 0L && beatDurationNanos > 0L) {
                    lastBeatTimeNanos = toChoreographerTimeNanos(stepTimeNanos)
                    currentBeatDurationNanos = beatDurationNanos
                    startChoreographerLoop()
                }
                if (beatIndex == 0) {
                    playheadResetID += 1
                }
            }

            if (rhythmFired) {
                activeRhythmIndex = rhythmIndex
                // Show the hit immediately; Choreographer owns the frame-synced decay.
                rhythmPulse = 1f
                if (stepTimeNanos > 0L && rhythmDurationNanos > 0L) {
                    lastRhythmTimeNanos = toChoreographerTimeNanos(stepTimeNanos)
                    currentRhythmDurationNanos = rhythmDurationNanos
                    startChoreographerLoop()
                }
            }
        }
    }

    private fun startChoreographerLoop() {
        if (choreographerCallback != null) return

        val frameChoreographer = choreographer ?: Choreographer.getInstance().also { choreographer = it }
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isPlaying || (lastBeatTimeNanos == 0L && lastRhythmTimeNanos == 0L)) {
                    choreographerCallback = null
                    return
                }

                updatePulseStates(frameTimeNanos)
                frameChoreographer.postFrameCallback(this)
            }
        }
        choreographerCallback = callback
        frameChoreographer.postFrameCallback(callback)
    }

    private fun stopChoreographerLoop() {
        choreographerCallback?.let { callback ->
            choreographer?.removeFrameCallback(callback)
        }
        choreographerCallback = null
    }

    private fun updatePulseStates(frameTimeNanos: Long) {
        if (lastBeatTimeNanos > 0L && currentBeatDurationNanos > 0L) {
            beatPulse = pulseAlpha(frameTimeNanos, lastBeatTimeNanos, currentBeatDurationNanos)
        }
        if (lastRhythmTimeNanos > 0L && currentRhythmDurationNanos > 0L) {
            rhythmPulse = pulseAlpha(frameTimeNanos, lastRhythmTimeNanos, currentRhythmDurationNanos)
        }
    }

    private fun pulseAlpha(frameTimeNanos: Long, startedAtNanos: Long, durationNanos: Long): Float {
        val progress = ((frameTimeNanos - startedAtNanos).toDouble() / durationNanos).coerceIn(0.0, 1.0)
        val remaining = 1.0 - progress
        return (remaining * remaining).toFloat()
    }

    private fun toChoreographerTimeNanos(elapsedRealtimeNanos: Long): Long {
        // Audio is scheduled with elapsedRealtimeNanos, while Choreographer frame times use nanoTime.
        return elapsedRealtimeNanos - SystemClock.elapsedRealtimeNanos() + System.nanoTime()
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        stop()
        audio.polyrhythmDelegate = null
    }
}
