package com.bfunkstudios.beatclikr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.MusicalEventRole
import com.bfunkstudios.beatclikr.music.PolyrhythmConfiguration
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PolyrhythmViewModel @Inject constructor(
    private val audio: IAudioPlayerService,
    private val playback: PlaybackObservation,
    private val prefs: IAppPreferences,
    secondaryOutputs: SecondaryOutputObservation
) : ViewModel() {

    var beats by mutableIntStateOf(prefs.polyrhythmBeats)
        private set

    var against by mutableIntStateOf(prefs.polyrhythmAgainst)
        private set

    var bpm by mutableFloatStateOf(prefs.polyrhythmBpm)
        private set

    private var transportState by mutableStateOf(playback.transportState.value)
    private var ownedSessionId: PlaybackSessionId? =
        transportState.sessionIdFor(PlaybackMode.POLYRHYTHM)
    private var awaitingOwnedSession = false

    val isPlaying: Boolean
        get() = transportState.isModeActive(PlaybackMode.POLYRHYTHM)

    val controlsEnabled: Boolean
        get() = !transportState.isModeTransitioning(PlaybackMode.POLYRHYTHM)

    val hasVariableOutputLatency: Boolean
        get() = transportState.hasVariableOutputLatency(PlaybackMode.POLYRHYTHM)

    val playbackStatus: PlaybackUiStatus?
        get() = transportState.uiStatus(PlaybackMode.POLYRHYTHM)

    var lastPlaybackDiagnostic by mutableStateOf<PlaybackUiDiagnostic?>(null)
        private set

    var lastSecondaryOutputFailure by mutableStateOf<SecondaryOutputFailure?>(null)
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

    private var lastBeatTimeNanos: Long = 0L
    private var currentBeatDurationNanos: Long = 0L
    private var lastRhythmTimeNanos: Long = 0L
    private var currentRhythmDurationNanos: Long = 0L
    private var projectedSessionId: PlaybackSessionId? = null
    private val committedEventCursor = CommittedEventDeliveryCursor(
        playback.committedEvents.replayCache.lastOrNull()?.sequence ?: 0L
    )
    private val pulseLoop = ChoreographerPulseLoop(
        shouldContinue = {
            isPlaying && (lastBeatTimeNanos != 0L || lastRhythmTimeNanos != 0L)
        },
        onFrame = ::updatePulseStates
    )
    var committedEventDeliveryLoss by mutableLongStateOf(0)
        private set

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

    fun updateBeats(value: Int) {
        beats = value.coerceIn(PolyrhythmConfiguration.SUPPORTED_COUNT)
        prefs.polyrhythmBeats = beats
        if (isPlaying) start()
    }

    fun updateAgainst(value: Int) {
        against = value.coerceIn(PolyrhythmConfiguration.SUPPORTED_COUNT)
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
        audio.submit(PlaybackIntent.SelectSounds(selectedBeatSound, selectedRhythmSound))
    }

    fun togglePlayPause() {
        if (isPlaying) stop() else start()
    }

    fun start() {
        val currentSession = transportState.sessionIdFor(PlaybackMode.POLYRHYTHM)
        ownedSessionId = currentSession
        awaitingOwnedSession = currentSession == null
        setupPolyrhythm()
        beatPulse = 0f
        rhythmPulse = 0f
        audio.submit(PlaybackIntent.SetMuted(prefs.muteMetronome))
        audio.submit(PlaybackIntent.SelectSoundBank(prefs.soundBank))
        audio.submit(
            PlaybackIntent.StartPolyrhythm(
                bpm,
                beats,
                against,
                PracticeItemSnapshot.polyrhythm()
            )
        )
    }

    fun stop() {
        ownedSessionId?.let { audio.submit(PlaybackIntent.StopIfCurrent(it)) }
        stopChoreographerLoop()
        beatPulse = 0f
        rhythmPulse = 0f
        lastBeatTimeNanos = 0L
        currentBeatDurationNanos = 0L
        lastRhythmTimeNanos = 0L
        currentRhythmDurationNanos = 0L
    }

    internal fun applyPolyrhythmEvent(
        beatFired: Boolean,
        rhythmFired: Boolean,
        beatIndex: Int,
        rhythmIndex: Int,
        stepTimeNanos: Long = 0L,
        beatDurationNanos: Long = 0L,
        rhythmDurationNanos: Long = 0L
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            if (beatFired) {
                activeBeatIndex = beatIndex
                // Show the hit immediately; Choreographer owns the frame-synced decay.
                beatPulse = 1f
                if (stepTimeNanos > 0L && beatDurationNanos > 0L) {
                    lastBeatTimeNanos = stepTimeNanos
                    currentBeatDurationNanos = beatDurationNanos
                    pulseLoop.start()
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
                    lastRhythmTimeNanos = stepTimeNanos
                    currentRhythmDurationNanos = rhythmDurationNanos
                    pulseLoop.start()
                }
            }
        }
    }

    private fun stopChoreographerLoop() {
        pulseLoop.stop()
    }

    private fun updatePulseStates(frameTimeNanos: Long) {
        if (lastBeatTimeNanos > 0L && currentBeatDurationNanos > 0L) {
            beatPulse = pulseAlpha(frameTimeNanos, lastBeatTimeNanos, currentBeatDurationNanos)
        }
        if (lastRhythmTimeNanos > 0L && currentRhythmDurationNanos > 0L) {
            rhythmPulse = pulseAlpha(frameTimeNanos, lastRhythmTimeNanos, currentRhythmDurationNanos)
        }
    }

    private fun applyTransportState(state: PlaybackTransportState) {
        transportState = state
        if (awaitingOwnedSession) {
            state.sessionIdFor(PlaybackMode.POLYRHYTHM)?.let { sessionId ->
                ownedSessionId = sessionId
                awaitingOwnedSession = false
            }
        }
        lastPlaybackDiagnostic = state.updateDiagnostic(lastPlaybackDiagnostic)
        val session = (state as? PlaybackTransportState.SessionState)
            ?.takeIf { it.context.mode == PlaybackMode.POLYRHYTHM }
            ?.context
            ?.sessionId
        if (session != null && session != projectedSessionId) {
            projectedSessionId = session
            playheadResetID += 1
        }
        if (!state.isModeActive(PlaybackMode.POLYRHYTHM)) {
            stopChoreographerLoop()
            beatPulse = 0f
            rhythmPulse = 0f
        }
    }

    private fun applyCommittedEvent(event: PlaybackCommittedEvent) {
        if (!deliverCommittedEvent(committedEventCursor, event) { gap ->
            committedEventDeliveryLoss += gap.missingCount
            stopChoreographerLoop()
            beatPulse = 0f
            rhythmPulse = 0f
        }) return
        val rendered = event as? PlaybackCommittedEvent.Rendered ?: return
        val playing = transportState as? PlaybackTransportState.Playing ?: return
        if (playing.context.mode != PlaybackMode.POLYRHYTHM ||
            playing.context.sessionId != rendered.sessionId) {
            return
        }
        val configuration =
            playing.context.configuration as CommittedPlaybackConfiguration.Polyrhythm
        val beatFired = rendered.role == MusicalEventRole.POLYRHYTHM_BEAT
        val rhythmFired = rendered.role == MusicalEventRole.POLYRHYTHM_RHYTHM
        val presentationTime = (rendered.presentation as? EventPresentation.Correlated)
            ?.presentationNanoTime
            ?: 0L
        val beatDurationNanos = polyrhythmBeatDurationNanos(configuration.bpm)
        val rhythmDurationNanos = polyrhythmRhythmDurationNanos(configuration)
        applyPolyrhythmEvent(
            beatFired,
            rhythmFired,
            if (beatFired) rendered.roleIndex else activeBeatIndex,
            if (rhythmFired) rendered.roleIndex else activeRhythmIndex,
            presentationTime,
            beatDurationNanos,
            rhythmDurationNanos
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopChoreographerLoop()
    }

}

internal fun polyrhythmBeatDurationNanos(bpm: Float): Long =
    (NANOS_PER_MINUTE / bpm.toDouble()).toLong()

internal fun polyrhythmRhythmDurationNanos(
    configuration: CommittedPlaybackConfiguration.Polyrhythm
): Long = (
    NANOS_PER_MINUTE * configuration.against /
        (configuration.bpm.toDouble() * configuration.beats)
    ).toLong()

private const val NANOS_PER_MINUTE = 60_000_000_000.0
