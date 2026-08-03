package com.bfunkstudios.beatclikr.ui

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.BeatPattern
import com.bfunkstudios.beatclikr.data.ClickerType
import com.bfunkstudios.beatclikr.data.Groove
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.CommittedPlaybackConfiguration
import com.bfunkstudios.beatclikr.services.CommittedEventDeliveryCursor
import com.bfunkstudios.beatclikr.services.deliverCommittedEvent
import com.bfunkstudios.beatclikr.services.EventPresentation
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.PlaybackCommittedEvent
import com.bfunkstudios.beatclikr.services.PlaybackIntent
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackObservation
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.PlaybackTransportState
import com.bfunkstudios.beatclikr.services.SecondaryOutputObservation
import com.bfunkstudios.beatclikr.services.SecondaryOutputFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MetronomeViewModel @Inject constructor(
    private val audio: IAudioPlayerService,
    private val playback: PlaybackObservation,
    private val prefs: IAppPreferences,
    secondaryOutputs: SecondaryOutputObservation
) : ViewModel() {

    var iconScale by mutableFloatStateOf(MetronomeConstants.ICON_SCALE_MIN)
        private set

    var beatPulse by mutableFloatStateOf(0f)
        private set

    private var transportState by mutableStateOf(playback.transportState.value)
    private var ownedSessionId: PlaybackSessionId? =
        transportState.sessionIdFor(PlaybackMode.STANDARD)
    private var awaitingOwnedSession = false
    private var replacedSessionId: PlaybackSessionId? = null

    val isPlaying: Boolean
        get() = transportState.isModeActive(PlaybackMode.STANDARD)

    val controlsEnabled: Boolean
        get() = !transportState.isModeTransitioning(PlaybackMode.STANDARD)

    val hasVariableOutputLatency: Boolean
        get() = transportState.hasVariableOutputLatency(PlaybackMode.STANDARD)

    val playbackStatus: PlaybackUiStatus?
        get() = transportState.uiStatus(PlaybackMode.STANDARD)

    var lastPlaybackDiagnostic by mutableStateOf<PlaybackUiDiagnostic?>(null)
        private set

    var lastSecondaryOutputFailure by mutableStateOf<SecondaryOutputFailure?>(null)
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
    private val tapTempoTracker = TapTempoTracker()
    var tapTempoFeedback by mutableStateOf<TapTempoFeedback?>(null)
        private set
    private var lastBeatTimeNanos: Long = 0L
    private var currentBeatDurationNanos: Long = 0L
    private val pendingBeatEvent = AtomicReference<PendingBeatEvent?>(null)
    private val committedEventCursor = CommittedEventDeliveryCursor(
        playback.committedEvents.replayCache.lastOrNull()?.sequence ?: 0L
    )
    private val pulseLoop = ChoreographerPulseLoop(
        shouldContinue = { isPlaying },
        onFrame = ::updateBeatPulse
    )
    var committedEventDeliveryLoss by mutableLongStateOf(0)
        private set

    private data class PendingBeatEvent(val timeNanos: Long, val durationNanos: Long)

    init {
        viewModelScope.launch {
            playback.transportState.collect(::applyTransportState)
        }
        viewModelScope.launch {
            playback.committedEvents.collect(::applyCommittedEvent)
        }
        viewModelScope.launch {
            secondaryOutputs.secondaryOutputFailure.collect { failure ->
                lastSecondaryOutputFailure = failure
            }
        }
    }

    fun playSong(song: Song) {
        selectSong(song, ClickerType.PLAYLIST)
        start(replaceCurrentSession = true)
    }

    fun returnToInstantMode() {
        if (clickerType == ClickerType.INSTANT) return
        if (isPlaying) stop()
        loadSong(Song.instantSong().copy(
            beatsPerMinute = prefs.instantBpm,
            groove = prefs.instantGroove,
            beatPattern = prefs.instantBeatPattern
        ))
    }

    fun loadSong(song: Song, type: ClickerType = ClickerType.INSTANT) {
        selectSong(song, type)
        if (isPlaying) pushCurrentStandardConfiguration()
    }

    private fun selectSong(song: Song, type: ClickerType) {
        currentSong = song
        clickerType = type
        selectedBeatSound = if (type == ClickerType.INSTANT) prefs.instantBeatSound else prefs.playlistBeatSound
        selectedRhythmSound = if (type == ClickerType.INSTANT) prefs.instantRhythmSound else prefs.playlistRhythmSound
        setupMetronomeFromSelection()
    }

    fun updateBPM(bpm: Float) {
        currentSong = currentSong.copy(
            beatsPerMinute = bpm.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
        )
        if (clickerType == ClickerType.INSTANT) prefs.instantBpm = currentSong.beatsPerMinute
        if (isPlaying) pushCurrentStandardConfiguration()
    }

    fun updateGroove(groove: Groove) {
        currentSong = currentSong.copy(
            groove = groove,
            beatPattern = if (groove.isOddMeter) selectedBeatPattern else currentSong.beatPattern
        )
        if (clickerType == ClickerType.INSTANT) prefs.instantGroove = currentSong.groove
        if (isPlaying) pushCurrentStandardConfiguration()
    }

    fun updateBeatPattern(pattern: BeatPattern) {
        currentSong = currentSong.copy(beatPattern = pattern)
        if (clickerType == ClickerType.INSTANT) prefs.instantBeatPattern = pattern
        if (isPlaying) pushCurrentStandardConfiguration()
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

    fun setupMetronome() = setupMetronomeFromSelection()

    fun refreshPlaybackSettings() {
        audio.submit(PlaybackIntent.SelectSoundBank(prefs.soundBank))
        if (isPlaying) pushCurrentStandardConfiguration()
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

    fun start() = start(replaceCurrentSession = false)

    private fun start(replaceCurrentSession: Boolean) {
        val currentSession = transportState.sessionIdFor(PlaybackMode.STANDARD)
        ownedSessionId = if (replaceCurrentSession) null else currentSession
        awaitingOwnedSession = replaceCurrentSession || currentSession == null
        replacedSessionId = if (replaceCurrentSession) currentSession else null
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
        audio.submit(PlaybackIntent.SetMuted(prefs.muteMetronome))
        audio.submit(PlaybackIntent.SelectSoundBank(prefs.soundBank))
        activeBpm = currentSong.beatsPerMinute
        rampController.reset()
        val bpm = currentSong.beatsPerMinute
        val subdivisions = getSubdivisionValue()
        val accentPattern = computeAccentPattern()
        val practiceItem = if (clickerType == ClickerType.INSTANT) {
            PracticeItemSnapshot.metronome()
        } else {
            PracticeItemSnapshot.fromSong(currentSong)
        }
        if (replaceCurrentSession) {
            audio.submit(
                PlaybackIntent.ReplaceStandard(
                    bpm,
                    subdivisions,
                    accentPattern,
                    prefs.sixteenthAlternate,
                    practiceItem
                )
            )
        } else {
            audio.submit(
                PlaybackIntent.StartStandard(
                    bpm,
                    subdivisions,
                    accentPattern,
                    prefs.sixteenthAlternate,
                    practiceItem
                )
            )
        }
    }

    fun stop() {
        val shouldRestoreRampBpm = rampEnabled && clickerType == ClickerType.INSTANT
        ownedSessionId?.let { audio.submit(PlaybackIntent.StopIfCurrent(it)) }
        rampController.reset()
        iconScale = MetronomeConstants.ICON_SCALE_MIN
        stopChoreographerLoop()
        beatPulse = 0f
        lastBeatTimeNanos = 0L
        currentBeatDurationNanos = 0L
        pendingBeatEvent.set(null)
        if (shouldRestoreRampBpm) {
            currentSong = currentSong.copy(beatsPerMinute = activeBpm)
        }
    }

    fun stopPlaybackForTopLevelNavigation() {
        audio.submit(PlaybackIntent.Stop)
    }

    fun recordTap(elapsedRealtimeNanos: Long = SystemClock.elapsedRealtimeNanos()) {
        val result = tapTempoTracker.record(elapsedRealtimeNanos)
        tapTempoFeedback = result.feedback
        result.bpm?.let { updateBPM(it.toFloat()) }
    }

    internal fun applyStandardEvent(
        isBeat: Boolean,
        beatInterval: Float,
        beatTimeNanos: Long
    ) {
        if (!isBeat) return
        // Avoid dispatch latency when handing the scheduled time to Choreographer.
        val hasScheduledBeatTime = beatTimeNanos > 0L
        if (hasScheduledBeatTime) {
            pendingBeatEvent.set(PendingBeatEvent(
                timeNanos = beatTimeNanos,
                durationNanos = (beatInterval * 1_000_000_000L).toLong().coerceAtLeast(1L)
            ))
        }
        viewModelScope.launch(Dispatchers.Main) {
            iconScale = MetronomeConstants.ICON_SCALE_MAX
            if (hasScheduledBeatTime) pulseLoop.start()
            handleBeat()
            delay(16)
            iconScale = MetronomeConstants.ICON_SCALE_MIN
        }
    }

    private fun handleBeat() {
        if (clickerType != ClickerType.INSTANT) return
        val newBpm = rampController.onBeat(currentSong.beatsPerMinute) ?: return
        currentSong = currentSong.copy(beatsPerMinute = newBpm)
        audio.submit(
            PlaybackIntent.UpdateStandard(
                newBpm,
                getSubdivisionValue(),
                computeAccentPattern(),
                prefs.sixteenthAlternate
            )
        )
    }

    private fun stopChoreographerLoop() {
        pulseLoop.stop()
    }

    private fun updateBeatPulse(frameTimeNanos: Long) {
        pendingBeatEvent.getAndSet(null)?.let { event ->
            lastBeatTimeNanos = event.timeNanos
            currentBeatDurationNanos = event.durationNanos
        }
        if (lastBeatTimeNanos == 0L || currentBeatDurationNanos == 0L) return
        beatPulse = pulseAlpha(frameTimeNanos, lastBeatTimeNanos, currentBeatDurationNanos)
    }

    private fun getSubdivisionValue(): Int = currentSong.groove.subdivisions

    private fun computeAccentPattern(): List<Boolean>? =
        if (currentSong.groove.isOddMeter) selectedBeatPattern.accentArray else null

    private fun pushCurrentStandardConfiguration() {
        audio.submit(
            PlaybackIntent.UpdateStandard(
                currentSong.beatsPerMinute,
                getSubdivisionValue(),
                computeAccentPattern(),
                prefs.sixteenthAlternate
            )
        )
    }

    private fun setupMetronomeFromSelection() {
        audio.submit(PlaybackIntent.SelectSounds(selectedBeatSound, selectedRhythmSound))
    }

    private fun applyTransportState(state: PlaybackTransportState) {
        transportState = state
        if (awaitingOwnedSession) {
            state.sessionIdFor(PlaybackMode.STANDARD)
                ?.takeIf { it != replacedSessionId }
                ?.let { sessionId ->
                    ownedSessionId = sessionId
                    awaitingOwnedSession = false
                    replacedSessionId = null
                }
        }
        lastPlaybackDiagnostic = state.updateDiagnostic(lastPlaybackDiagnostic)
        if (!state.isModeActive(PlaybackMode.STANDARD)) {
            stopChoreographerLoop()
            pendingBeatEvent.set(null)
            iconScale = MetronomeConstants.ICON_SCALE_MIN
            beatPulse = 0f
        }
    }

    private fun applyCommittedEvent(event: PlaybackCommittedEvent) {
        if (!deliverCommittedEvent(committedEventCursor, event) { gap ->
            committedEventDeliveryLoss += gap.missingCount
            stopChoreographerLoop()
            pendingBeatEvent.set(null)
            iconScale = MetronomeConstants.ICON_SCALE_MIN
            beatPulse = 0f
        }) return
        val rendered = event as? PlaybackCommittedEvent.Rendered ?: return
        val playing = transportState as? PlaybackTransportState.Playing ?: return
        if (playing.context.mode != PlaybackMode.STANDARD ||
            playing.context.sessionId != rendered.sessionId) {
            return
        }
        val configuration =
            playing.context.configuration as CommittedPlaybackConfiguration.Standard
        val index = rendered.roleIndex
        val isBeat = configuration.accentPattern?.getOrNull(index) ?: (index == 0)
        val beatInterval = 60f / configuration.bpm
        val presentationTime = (rendered.presentation as? EventPresentation.Correlated)
            ?.presentationNanoTime
            ?: 0L
        applyStandardEvent(isBeat, beatInterval, presentationTime)
    }

    override fun onCleared() {
        super.onCleared()
        stopChoreographerLoop()
        pendingBeatEvent.set(null)
    }
}
