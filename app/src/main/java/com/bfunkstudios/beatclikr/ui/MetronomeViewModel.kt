package com.bfunkstudios.beatclikr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.BeatPattern
import com.bfunkstudios.beatclikr.data.ClickerType
import com.bfunkstudios.beatclikr.data.Groove
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepository
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.IFlashlightService
import com.bfunkstudios.beatclikr.services.IHapticFeedbackService
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MetronomeViewModel @Inject constructor(
    private val audio: IAudioPlayerService,
    private val prefs: IAppPreferences,
    private val practiceHistory: PracticeHistoryRepository,
    private val flashlight: IFlashlightService,
    private val haptics: IHapticFeedbackService
) : ViewModel(), MetronomeAudioEngineDelegate {

    var iconScale by mutableFloatStateOf(MetronomeConstants.ICON_SCALE_MIN)
        private set

    var beatPulse by mutableFloatStateOf(0f)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var clickerType by mutableStateOf(ClickerType.INSTANT)
        private set

    var currentSong by mutableStateOf(
        Song.instantSong().copy(
            beatsPerMinute = prefs.instantBpm,
            groove = prefs.instantGroove,
            beatPattern = prefs.instantBeatPattern
        )
    )
        private set

    val beatsPerMinute: Float get() = currentSong.beatsPerMinute
    val selectedGroove: Groove get() = currentSong.groove
    val selectedBeatPattern: BeatPattern get() = currentSong.beatPattern ?: BeatPattern.default

    var selectedBeatSound by mutableStateOf(prefs.instantBeatSound)
        private set

    var selectedRhythmSound by mutableStateOf(prefs.instantRhythmSound)
        private set

    var rampEnabled by mutableStateOf(prefs.rampEnabled)
        private set

    var rampIncrement by mutableStateOf(prefs.rampIncrement)
        private set

    var rampInterval by mutableStateOf(prefs.rampInterval)
        private set

    private var activeBpm: Float = prefs.instantBpm
    private val rampController = RampController(
        enabled = prefs.rampEnabled,
        increment = prefs.rampIncrement,
        interval = prefs.rampInterval
    )
    private val tapTimestamps = mutableListOf<Long>()
    private var beatPulseJob: Job? = null

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) {
            if (isPlaying) stop()
        }
    }

    init {
        audio.delegate = this
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
    }

    fun playSong(song: Song) {
        loadSong(song, ClickerType.PLAYLIST)
        start()
        viewModelScope.launch { practiceHistory.recordSongPlayed(song) }
    }

    fun loadSong(song: Song, type: ClickerType = ClickerType.INSTANT) {
        currentSong = song
        clickerType = type
        selectedBeatSound = if (type == ClickerType.INSTANT) prefs.instantBeatSound else prefs.playlistBeatSound
        selectedRhythmSound = if (type == ClickerType.INSTANT) prefs.instantRhythmSound else prefs.playlistRhythmSound
        setupMetronomeFromSelection()
        if (isPlaying) {
            audio.updateTempo(
                currentSong.beatsPerMinute,
                getSubdivisionValue(),
                computeAccentPattern(),
                prefs.sixteenthAlternate
            )
        }
    }

    fun updateBPM(bpm: Float) {
        currentSong = currentSong.copy(
            beatsPerMinute = bpm.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
        )
        if (clickerType == ClickerType.INSTANT) prefs.instantBpm = currentSong.beatsPerMinute
        if (isPlaying) {
            audio.updateTempo(
                currentSong.beatsPerMinute,
                getSubdivisionValue(),
                computeAccentPattern(),
                prefs.sixteenthAlternate
            )
        }
    }

    fun updateGroove(groove: Groove) {
        currentSong = currentSong.copy(
            groove = groove,
            beatPattern = if (groove.isOddMeter) selectedBeatPattern else currentSong.beatPattern
        )
        if (clickerType == ClickerType.INSTANT) prefs.instantGroove = currentSong.groove
        if (isPlaying) {
            audio.updateTempo(
                currentSong.beatsPerMinute,
                getSubdivisionValue(),
                computeAccentPattern(),
                prefs.sixteenthAlternate
            )
        }
    }

    fun updateBeatPattern(pattern: BeatPattern) {
        currentSong = currentSong.copy(beatPattern = pattern)
        if (clickerType == ClickerType.INSTANT) prefs.instantBeatPattern = pattern
        if (isPlaying) {
            audio.updateTempo(
                currentSong.beatsPerMinute,
                getSubdivisionValue(),
                computeAccentPattern(),
                prefs.sixteenthAlternate
            )
        }
    }

    fun updateRampEnabled(enabled: Boolean) {
        rampEnabled = enabled
        rampController.enabled = enabled
        if (clickerType == ClickerType.INSTANT) prefs.rampEnabled = enabled
    }

    fun updateRampIncrement(increment: Int) {
        rampIncrement = increment.coerceAtLeast(1)
        rampController.increment = rampIncrement
        if (clickerType == ClickerType.INSTANT) prefs.rampIncrement = rampIncrement
    }

    fun updateRampInterval(interval: Int) {
        rampInterval = interval.coerceAtLeast(1)
        rampController.interval = rampInterval
        if (clickerType == ClickerType.INSTANT) prefs.rampInterval = rampInterval
    }

    fun updateBeatSound(sound: SoundFile) {
        selectedBeatSound = sound
        if (clickerType == ClickerType.INSTANT) {
            prefs.instantBeatSound = sound
        } else {
            prefs.playlistBeatSound = sound
        }
        setupMetronomeFromSelection()
    }

    fun updateRhythmSound(sound: SoundFile) {
        selectedRhythmSound = sound
        if (clickerType == ClickerType.INSTANT) {
            prefs.instantRhythmSound = sound
        } else {
            prefs.playlistRhythmSound = sound
        }
        setupMetronomeFromSelection()
    }

    fun setupMetronome(beatResourceId: Int, rhythmResourceId: Int) {
        audio.setupAudioPlayer(beatResourceId, rhythmResourceId)
    }

    fun refreshPlaybackSettings() {
        if (isPlaying) {
            audio.updateTempo(
                currentSong.beatsPerMinute,
                getSubdivisionValue(),
                computeAccentPattern(),
                prefs.sixteenthAlternate
            )
        }
    }

    fun applyMetronomeSoundSettings(beat: SoundFile, rhythm: SoundFile) {
        if (clickerType != ClickerType.INSTANT) return
        selectedBeatSound = beat
        selectedRhythmSound = rhythm
        setupMetronomeFromSelection()
    }

    fun togglePlayPause() {
        if (isPlaying) stop() else start()
    }

    fun start() {
        if (clickerType == ClickerType.INSTANT) {
            selectedBeatSound = prefs.instantBeatSound
            selectedRhythmSound = prefs.instantRhythmSound
            currentSong = Song.instantSong().copy(
                beatsPerMinute = currentSong.beatsPerMinute,
                groove = currentSong.groove,
                beatPattern = currentSong.beatPattern
            )
            setupMetronomeFromSelection()
        }
        audio.isMuted = prefs.muteMetronome
        activeBpm = currentSong.beatsPerMinute
        rampController.reset()
        audio.startMetronome(
            currentSong.beatsPerMinute,
            getSubdivisionValue(),
            computeAccentPattern(),
            prefs.sixteenthAlternate
        )
        isPlaying = true
        if (clickerType == ClickerType.INSTANT) {
            viewModelScope.launch { practiceHistory.recordMetronomePractice() }
        }
    }

    fun stop() {
        val shouldRestoreRampBpm = rampEnabled && clickerType == ClickerType.INSTANT
        audio.stopMetronome()
        flashlight.turnFlashlightOff()
        rampController.reset()
        isPlaying = false
        iconScale = MetronomeConstants.ICON_SCALE_MIN
        beatPulseJob?.cancel()
        beatPulse = 0f
        if (shouldRestoreRampBpm) {
            currentSong = currentSong.copy(beatsPerMinute = activeBpm)
        }
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

    override fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float) {
        viewModelScope.launch(Dispatchers.Main) {
            if (isBeat) {
                iconScale = MetronomeConstants.ICON_SCALE_MAX
                beatPulseJob?.cancel()
                beatPulseJob = launch {
                    fadeBeatPulse((beatInterval * 1000).toLong().coerceAtLeast(1L))
                }
                handleBeat()
                delay(16)
                iconScale = MetronomeConstants.ICON_SCALE_MIN
            } else {
                handleRhythm()
            }
        }
    }

    private fun handleBeat() {
        if (prefs.useFlashlight) flashlight.turnFlashlightOn()
        if (prefs.useVibration) haptics.playBeatHaptic()
        if (clickerType != ClickerType.INSTANT) return
        val newBpm = rampController.onBeat(currentSong.beatsPerMinute) ?: return
        currentSong = currentSong.copy(beatsPerMinute = newBpm)
        audio.updateTempo(newBpm, getSubdivisionValue(), computeAccentPattern(), prefs.sixteenthAlternate)
    }

    private fun handleRhythm() {
        if (prefs.useFlashlight) {
            flashlight.turnFlashlightOff()
        }
        if (prefs.useVibration) {
            haptics.playRhythmHaptic()
        }
    }

    private suspend fun fadeBeatPulse(durationMillis: Long) {
        beatPulse = 1f
        val frameMillis = 16L
        var elapsedMillis = 0L
        while (currentCoroutineContext().isActive && elapsedMillis < durationMillis) {
            delay(frameMillis)
            elapsedMillis += frameMillis
            val progress = (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
            beatPulse = 1f - progress
        }
        beatPulse = 0f
    }

    private fun getSubdivisionValue(): Int = currentSong.groove.subdivisions

    private fun computeAccentPattern(): List<Boolean>? =
        if (currentSong.groove.isOddMeter) selectedBeatPattern.accentArray else null

    private fun setupMetronomeFromSelection() {
        val beatResId = selectedBeatSound.resourceId
        val rhythmResId = selectedRhythmSound.resourceId
        if (beatResId != null && rhythmResId != null) setupMetronome(beatResId, rhythmResId)
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        stop()
        audio.delegate = null
    }
}
