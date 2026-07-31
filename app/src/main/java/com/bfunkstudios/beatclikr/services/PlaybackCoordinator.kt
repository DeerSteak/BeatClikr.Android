package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.MusicalEventRole
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackMode {
    NONE,
    STANDARD,
    POLYRHYTHM
}

data class RequestedSoundConfiguration(
    val bank: SoundBank,
    val beatSound: SoundFile,
    val rhythmSound: SoundFile
)

data class PlaybackOwnershipSnapshot(
    val activeMode: PlaybackMode = PlaybackMode.NONE,
    val muted: Boolean = false,
    val requestedSounds: RequestedSoundConfiguration = RequestedSoundConfiguration(
        SoundBank.ACOUSTIC,
        SoundFile.CLICK_HI,
        SoundFile.CLICK_LO
    ),
    val audibleSounds: ActiveSoundConfiguration? = null,
    val soundPreparationFailure: SoundPreparationFailure? = null,
    val lastCommandSequence: Long = 0,
    val lastOutcome: PlaybackIntentOutcome? = null
)

sealed interface PlaybackIntent {
    data class Invalid(val diagnostic: String) : PlaybackIntent
    data class SelectSounds(val beat: SoundFile, val rhythm: SoundFile) : PlaybackIntent
    data class SelectSoundBank(val bank: SoundBank) : PlaybackIntent
    data class SetMuted(val muted: Boolean) : PlaybackIntent
    data class StartStandard(
        val bpm: Float,
        val subdivisions: Int,
        val accentPattern: List<Boolean>?,
        val alternateSixteenth: Boolean
    ) : PlaybackIntent
    data class ReplaceStandard(
        val bpm: Float,
        val subdivisions: Int,
        val accentPattern: List<Boolean>?,
        val alternateSixteenth: Boolean
    ) : PlaybackIntent
    data class UpdateStandard(
        val bpm: Float,
        val subdivisions: Int,
        val accentPattern: List<Boolean>?,
        val alternateSixteenth: Boolean
    ) : PlaybackIntent
    data class StartPolyrhythm(
        val bpm: Float,
        val beats: Int,
        val against: Int
    ) : PlaybackIntent
    data class UpdatePolyrhythm(
        val bpm: Float,
        val beats: Int,
        val against: Int
    ) : PlaybackIntent
    data class StopIfCurrent(val expectedSessionId: PlaybackSessionId) : PlaybackIntent
    data object Stop : PlaybackIntent
    data object Prewarm : PlaybackIntent
    data class PrepareSounds(val sounds: Collection<SoundFile>) : PlaybackIntent
}

sealed interface PlaybackSystemInput {
    data class Interrupted(
        val sessionId: PlaybackSessionId,
        val reason: PlaybackInterruptionReason
    ) : PlaybackSystemInput

    data class EngineFailed(
        val sessionId: PlaybackSessionId,
        val diagnostic: String
    ) : PlaybackSystemInput
}

enum class PlaybackCoordinatorFailureCode {
    INVALID_INPUT,
    SOUND_PREPARATION_FAILED,
    MODE_MISMATCH,
    STALE_SESSION,
    RENDERER_REJECTED,
    SUPERSEDED,
    ENGINE_FAILURE,
    RELEASED
}

data class PlaybackCoordinatorFailure(
    val code: PlaybackCoordinatorFailureCode,
    val diagnostic: String
)

sealed interface PlaybackIntentOutcome {
    val commandSequence: Long

    data class Accepted(override val commandSequence: Long) : PlaybackIntentOutcome
    data class Rejected(
        override val commandSequence: Long,
        val failure: PlaybackCoordinatorFailure
    ) : PlaybackIntentOutcome
}

sealed interface PlaybackTimingEvent {
    data class StandardTiming(
        val isBeat: Boolean,
        val beatInterval: Float,
        val beatTimeNanos: Long
    ) : PlaybackTimingEvent

    data class PolyrhythmTiming(
        val beatFired: Boolean,
        val rhythmFired: Boolean,
        val beatIndex: Int,
        val rhythmIndex: Int,
        val stepTimeNanos: Long,
        val beatDurationNanos: Long,
        val rhythmDurationNanos: Long
    ) : PlaybackTimingEvent
}

sealed interface PlaybackControlEvent {
    data class IntentCompleted(
        val commandSequence: Long,
        val intent: PlaybackIntent,
        val outcome: PlaybackIntentOutcome
    ) : PlaybackControlEvent

    data class SoundPreparationFailed(
        val commandSequence: Long,
        val failure: SoundPreparationFailure
    ) : PlaybackControlEvent

    data class SystemInputRejected(
        val commandSequence: Long,
        val input: PlaybackSystemInput,
        val failure: PlaybackCoordinatorFailure
    ) : PlaybackControlEvent

    data class UnexpectedFailure(val diagnostic: String) : PlaybackControlEvent
}

data class PlaybackStateTransition(
    val sequence: Long,
    val from: PlaybackTransportState,
    val to: PlaybackTransportState
)

sealed interface PlaybackCommittedEvent {
    val sequence: Long

    data class StateChanged(
        override val sequence: Long,
        val transition: PlaybackStateTransition
    ) : PlaybackCommittedEvent

    data class FirstEventScheduled(
        override val sequence: Long,
        val sessionId: PlaybackSessionId,
        val intendedFrame: Long
    ) : PlaybackCommittedEvent

    data class Rendered(
        override val sequence: Long,
        val sessionId: PlaybackSessionId,
        val eventSequence: Long,
        val role: MusicalEventRole,
        val intendedFrame: Long,
        val muted: Boolean,
        val presentation: EventPresentation,
        val roleIndex: Int = 0
    ) : PlaybackCommittedEvent

    data class RecordsDropped(
        override val sequence: Long,
        val count: Long
    ) : PlaybackCommittedEvent
}

sealed interface EventPresentation {
    data object Unavailable : EventPresentation

    data class Correlated(
        val presentationNanoTime: Long,
        val correlation: AudioFrameCorrelation
    ) : EventPresentation
}

interface PlaybackEnginePort {
    var soundPreparationObserver: ((SoundPreparationPublication) -> Unit)?
    var transportObserver: PlaybackEngineTransportObserver?
    var delegate: MetronomeAudioEngineDelegate?
    var polyrhythmDelegate: PolyrhythmAudioEngineDelegate?
    var isMuted: Boolean

    fun activeSoundConfiguration(): ActiveSoundConfiguration?
    fun soundPreparationFailure(): SoundPreparationFailure?
    fun prewarmAudioTrack()
    fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot?
    fun release()
    fun beginStandardSession(
        sessionId: PlaybackSessionId,
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    )
    fun beginPolyrhythmSession(
        sessionId: PlaybackSessionId,
        bpm: Float,
        beats: Int,
        against: Int
    )
    fun updateStandardSession(
        sessionId: PlaybackSessionId,
        configuration: CommittedPlaybackConfiguration.Standard,
        completion: (PlaybackEngineUpdateResult) -> Unit
    )
    fun updatePolyrhythmSession(
        sessionId: PlaybackSessionId,
        configuration: CommittedPlaybackConfiguration.Polyrhythm,
        completion: (PlaybackEngineUpdateResult) -> Unit
    )
    fun stopSession(sessionId: PlaybackSessionId, mode: PlaybackMode)
    fun drainRenderedEvents(afterCaptureSequence: Long): FrameAudioRenderedEventBatch?
    fun selectSounds(
        requestSequence: Long,
        beatResourceId: Int,
        rhythmResourceId: Int
    )
    fun selectSoundBank(requestSequence: Long, bank: SoundBank)
    fun prepareSounds(requestSequence: Long, sounds: Collection<SoundFile>)
}

sealed interface PlaybackEngineUpdateResult {
    val sessionId: PlaybackSessionId

    data class Accepted(override val sessionId: PlaybackSessionId) : PlaybackEngineUpdateResult

    data class Rejected(
        override val sessionId: PlaybackSessionId,
        val reason: Reason,
        val diagnostic: String? = null
    ) : PlaybackEngineUpdateResult

    enum class Reason {
        STALE_SESSION,
        INACTIVE_MODE,
        RENDERER_REJECTED,
        INVALID_CONFIGURATION,
        ENGINE_FAILURE
    }
}

data class SoundPreparationPublication(
    val requestSequence: Long?,
    val sessionId: PlaybackSessionId?,
    val adopted: Boolean,
    val active: ActiveSoundConfiguration?,
    val failure: SoundPreparationFailure?
)

data class PlaybackEngineStartEvidence(
    val sessionId: PlaybackSessionId,
    val audibleSounds: ActiveSoundConfiguration,
    val route: AudioOutputRoute,
    val backend: AudioBackendType,
    val firstEventFrame: Long
)

interface PlaybackEngineTransportObserver {
    fun engineStarted(evidence: PlaybackEngineStartEvidence)
    fun audioFocusUnavailable(sessionId: PlaybackSessionId)
    fun engineStartFailed(sessionId: PlaybackSessionId, diagnostic: String)
    fun engineStopped(sessionId: PlaybackSessionId)
    fun engineInterrupted(
        sessionId: PlaybackSessionId,
        reason: PlaybackInterruptionReason
    )
}

class PlaybackCoordinator(
    private val engine: PlaybackEnginePort,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PlaybackCoordinatorControl")
    },
    private val eventDrainScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "PlaybackCoordinatorEvents")
        }
) : IAudioPlayerService, MetronomeAudioEngineDelegate, PolyrhythmAudioEngineDelegate,
    PlaybackEngineTransportObserver, PlaybackObservation {
    private val mutableOwnership = MutableStateFlow(PlaybackOwnershipSnapshot())
    private val mutableTimingEvents = MutableSharedFlow<PlaybackTimingEvent>(
        extraBufferCapacity = TIMING_EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val mutableControlEvents = MutableSharedFlow<PlaybackControlEvent>(
        replay = CONTROL_EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val mutableTransportState =
        MutableStateFlow<PlaybackTransportState>(PlaybackTransportState.Idle)
    private val mutableStateTransitions = MutableSharedFlow<PlaybackStateTransition>(
        replay = TRANSPORT_EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val mutableCommittedEvents = MutableSharedFlow<PlaybackCommittedEvent>(
        replay = TRANSPORT_EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var nextCommandSequence = 1L
    private var nextSessionId = 1L
    private var nextTransitionSequence = 1L
    private var nextCommittedEventSequence = 1L
    private var nextEngineCaptureSequence = 0L
    private var lastBackendFailure: AudioBackendFailure? = null
    private var eventDrainFuture: ScheduledFuture<*>? = null
    private var pendingReplacement: PendingStart? = null
    private val pendingUpdates = ArrayDeque<PendingUpdate>()
    private var inFlightUpdate: PendingUpdate? = null
    private var latestSoundRequestSequence = 0L
    @Volatile
    private var controlThread: Thread? = null
    @Volatile
    private var released = false

    val ownership: StateFlow<PlaybackOwnershipSnapshot> = mutableOwnership
    val timingEvents: SharedFlow<PlaybackTimingEvent> = mutableTimingEvents
    val controlEvents: SharedFlow<PlaybackControlEvent> = mutableControlEvents
    override val transportState: StateFlow<PlaybackTransportState> = mutableTransportState
    val stateTransitions: SharedFlow<PlaybackStateTransition> = mutableStateTransitions
    override val committedEvents: SharedFlow<PlaybackCommittedEvent> = mutableCommittedEvents

    @Volatile
    override var delegate: MetronomeAudioEngineDelegate? = null

    @Volatile
    override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate? = null

    init {
        engine.soundPreparationObserver = ::onSoundPreparation
        engine.transportObserver = this
        engine.delegate = this
        engine.polyrhythmDelegate = this
        refreshAudibleSounds()
    }

    override var isMuted: Boolean
        get() = ownership.value.muted
        set(value) {
            submit(PlaybackIntent.SetMuted(value))
        }

    override var soundBank: SoundBank
        get() = ownership.value.requestedSounds.bank
        set(value) {
            submit(PlaybackIntent.SelectSoundBank(value))
        }

    @Synchronized
    fun submit(intent: PlaybackIntent): Long {
        val sequence = nextCommandSequence++
        val publishedIntent = intent.immutableCopy()
        if (released) {
            recordOutcome(
                sequence,
                publishedIntent,
                PlaybackIntentOutcome.Rejected(
                    sequence,
                    PlaybackCoordinatorFailure(
                        PlaybackCoordinatorFailureCode.RELEASED,
                        "Playback coordinator is released"
                    )
                )
            )
        } else {
            executeControl("Playback intent failed") {
                applyIntent(sequence, publishedIntent)
            }
        }
        return sequence
    }

    @Synchronized
    fun submitSystemInput(input: PlaybackSystemInput): Long {
        val sequence = nextCommandSequence++
        if (released) {
            mutableControlEvents.tryEmit(
                PlaybackControlEvent.SystemInputRejected(
                    sequence,
                    input,
                    PlaybackCoordinatorFailure(
                        PlaybackCoordinatorFailureCode.RELEASED,
                        "Playback coordinator is released"
                    )
                )
            )
        } else {
            executeControl("Playback system input failed") {
                applySystemInput(input)
            }
        }
        return sequence
    }

    override fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int) {
        val beat = SoundFile.fromResourceId(beatResourceId)
        val rhythm = SoundFile.fromResourceId(rhythmResourceId)
        if (beat == null || rhythm == null) {
            submit(PlaybackIntent.Invalid("Unknown sound resource"))
            return
        }
        submit(PlaybackIntent.SelectSounds(beat, rhythm))
    }

    override fun startMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {
        submit(
            PlaybackIntent.StartStandard(
                bpm,
                subdivisions,
                accentPattern?.toList(),
                alternateSixteenth
            )
        )
    }

    override fun replaceMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {
        submit(
            PlaybackIntent.ReplaceStandard(
                bpm,
                subdivisions,
                accentPattern?.toList(),
                alternateSixteenth
            )
        )
    }

    override fun stopIfCurrent(expectedSessionId: PlaybackSessionId) {
        submit(PlaybackIntent.StopIfCurrent(expectedSessionId))
    }

    override fun updateTempo(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {
        submit(
            PlaybackIntent.UpdateStandard(
                bpm,
                subdivisions,
                accentPattern?.toList(),
                alternateSixteenth
            )
        )
    }

    override fun startPolyrhythm(bpm: Float, beats: Int, against: Int) {
        submit(PlaybackIntent.StartPolyrhythm(bpm, beats, against))
    }

    override fun stopPlayback() {
        submit(PlaybackIntent.Stop)
    }

    override fun prewarmAudioTrack() {
        submit(PlaybackIntent.Prewarm)
    }

    override fun prepareAudioTrackSounds(soundFiles: Collection<SoundFile>) {
        submit(PlaybackIntent.PrepareSounds(soundFiles.toList()))
    }

    override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? =
        engine.getFrameAudioMetricsSnapshot()

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        val sequence = nextCommandSequence++
        executeControl("Playback release failed") {
            try {
                try {
                    applyStop(sequence)
                } catch (failure: RuntimeException) {
                    handleTransportFailure(
                        failure.message ?: "Playback engine rejected the stop"
                    )
                }
                engine.release()
            } finally {
                val current = transportState.value
                if (current is PlaybackTransportState.SessionState) {
                    publishRenderedEvents(
                        current.context.sessionId,
                        detectRuntimeFailure = false
                    )
                }
                engine.soundPreparationObserver = null
                engine.transportObserver = null
                engine.delegate = null
                engine.polyrhythmDelegate = null
                settleReleasedTransport()
            }
        }
        executor.shutdown()
        eventDrainScheduler.shutdownNow()
        delegate = null
        polyrhythmDelegate = null
    }

    override fun metronomeBeatFired(
        isBeat: Boolean,
        beatInterval: Float,
        beatTimeNanos: Long
    ) {
        onControlContext(::publishRenderedEvents)
        mutableTimingEvents.tryEmit(
            PlaybackTimingEvent.StandardTiming(isBeat, beatInterval, beatTimeNanos)
        )
        delegate?.metronomeBeatFired(isBeat, beatInterval, beatTimeNanos)
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
        onControlContext(::publishRenderedEvents)
        mutableTimingEvents.tryEmit(
            PlaybackTimingEvent.PolyrhythmTiming(
                beatFired,
                rhythmFired,
                beatIndex,
                rhythmIndex,
                stepTimeNanos,
                beatDurationNanos,
                rhythmDurationNanos
            )
        )
        polyrhythmDelegate?.polyrhythmBeatFired(
            beatFired,
            rhythmFired,
            beatIndex,
            rhythmIndex,
            stepTimeNanos,
            beatDurationNanos,
            rhythmDurationNanos
        )
    }

    override fun polyrhythmStartFailed() {
        polyrhythmDelegate?.polyrhythmStartFailed()
    }

    override fun metronomeStartFailed() {
        delegate?.metronomeStartFailed()
    }

    override fun engineStarted(evidence: PlaybackEngineStartEvidence) {
        onControlContext { applyEngineStarted(evidence) }
    }

    override fun engineStartFailed(sessionId: PlaybackSessionId, diagnostic: String) {
        onControlContext { applyEngineStartFailed(sessionId, diagnostic) }
    }

    override fun audioFocusUnavailable(sessionId: PlaybackSessionId) {
        onControlContext { applyAudioFocusUnavailable(sessionId) }
    }

    override fun engineStopped(sessionId: PlaybackSessionId) {
        onControlContext { applyEngineStopped(sessionId) }
    }

    override fun engineInterrupted(
        sessionId: PlaybackSessionId,
        reason: PlaybackInterruptionReason
    ) {
        onControlContext {
            applySystemInput(PlaybackSystemInput.Interrupted(sessionId, reason))
        }
    }

    internal fun awaitControlIdle(timeoutSeconds: Long = 5): Boolean {
        if (executor.isShutdown) return executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)
        return try {
            executor.submit {}.get(timeoutSeconds, TimeUnit.SECONDS)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun applyIntent(sequence: Long, intent: PlaybackIntent) {
        controlThread = Thread.currentThread()
        val validationFailure = validate(intent)
        if (validationFailure != null) {
            recordOutcome(
                sequence,
                intent,
                rejectedOutcome(
                    sequence,
                    PlaybackCoordinatorFailureCode.INVALID_INPUT,
                    validationFailure
                )
            )
            return
        }
        val modeFailure = validateMode(intent)
        if (modeFailure != null) {
            recordOutcome(
                sequence,
                intent,
                rejectedOutcome(
                    sequence,
                    PlaybackCoordinatorFailureCode.MODE_MISMATCH,
                    modeFailure
                )
            )
            return
        }
        val outcome: PlaybackIntentOutcome? = try {
            var awaitsEngineAcknowledgement = false
            when (intent) {
                is PlaybackIntent.Invalid -> error("Invalid intent passed validation")
                is PlaybackIntent.SelectSounds -> {
                    latestSoundRequestSequence = sequence
                    updateRequestedSounds(beat = intent.beat, rhythm = intent.rhythm)
                    engine.selectSounds(
                        sequence,
                        requireNotNull(intent.beat.resourceId),
                        requireNotNull(intent.rhythm.resourceId)
                    )
                }
                is PlaybackIntent.SelectSoundBank -> {
                    latestSoundRequestSequence = sequence
                    updateRequestedSounds(bank = intent.bank)
                    engine.selectSoundBank(sequence, intent.bank)
                }
                is PlaybackIntent.SetMuted -> {
                    engine.isMuted = intent.muted
                    mutateOwnership { it.copy(muted = intent.muted) }
                    amendTransportConfiguration { configuration ->
                        when (configuration) {
                            is CommittedPlaybackConfiguration.Standard ->
                                configuration.copy(muted = intent.muted)
                            is CommittedPlaybackConfiguration.Polyrhythm ->
                                configuration.copy(muted = intent.muted)
                        }
                    }
                }
                is PlaybackIntent.StartStandard -> {
                    if (transportState.value.activeModeOrNone() == PlaybackMode.STANDARD) {
                        queueUpdate(sequence, intent, standardConfiguration(intent))
                        awaitsEngineAcknowledgement = true
                    } else {
                        replaceOrStart(
                            PendingStart.Standard(
                                newSessionId(),
                                intent,
                                ownership.value.muted
                            )
                        )
                    }
                }
                is PlaybackIntent.ReplaceStandard -> replaceOrStart(
                    PendingStart.Standard(
                        newSessionId(),
                        intent.asStartIntent(),
                        ownership.value.muted
                    )
                )
                is PlaybackIntent.UpdateStandard -> {
                    queueUpdate(sequence, intent, standardConfiguration(intent))
                    awaitsEngineAcknowledgement = true
                }
                is PlaybackIntent.StartPolyrhythm -> {
                    if (transportState.value.activeModeOrNone() == PlaybackMode.POLYRHYTHM) {
                        queueUpdate(sequence, intent, polyrhythmConfiguration(intent))
                        awaitsEngineAcknowledgement = true
                    } else {
                        replaceOrStart(
                            PendingStart.Polyrhythm(
                                newSessionId(),
                                intent,
                                ownership.value.muted
                            )
                        )
                    }
                }
                is PlaybackIntent.UpdatePolyrhythm -> {
                    queueUpdate(sequence, intent, polyrhythmConfiguration(intent))
                    awaitsEngineAcknowledgement = true
                }
                is PlaybackIntent.StopIfCurrent -> {
                    applyStop(sequence, intent.expectedSessionId)
                }
                PlaybackIntent.Stop -> applyStop(sequence)
                PlaybackIntent.Prewarm -> engine.prewarmAudioTrack()
                is PlaybackIntent.PrepareSounds -> {
                    latestSoundRequestSequence = sequence
                    engine.prepareSounds(sequence, intent.sounds)
                }
            }
            if (awaitsEngineAcknowledgement) null else PlaybackIntentOutcome.Accepted(sequence)
        } catch (failure: RuntimeException) {
            handleTransportFailure(
                failure.message ?: "Playback engine rejected the command"
            )
            rejectedOutcome(
                sequence,
                PlaybackCoordinatorFailureCode.ENGINE_FAILURE,
                failure.message ?: "Playback engine rejected the command"
            )
        }
        outcome?.let { recordOutcome(sequence, intent, it) }
    }

    private fun standardConfiguration(intent: PlaybackIntent.StartStandard) =
        CommittedPlaybackConfiguration.Standard(
            intent.bpm,
            intent.subdivisions,
            intent.accentPattern?.toList(),
            intent.alternateSixteenth,
            ownership.value.muted
        )

    private fun standardConfiguration(intent: PlaybackIntent.UpdateStandard) =
        CommittedPlaybackConfiguration.Standard(
            intent.bpm,
            intent.subdivisions,
            intent.accentPattern?.toList(),
            intent.alternateSixteenth,
            ownership.value.muted
        )

    private fun polyrhythmConfiguration(intent: PlaybackIntent.StartPolyrhythm) =
        CommittedPlaybackConfiguration.Polyrhythm(
            intent.bpm,
            intent.beats,
            intent.against,
            ownership.value.muted
        )

    private fun polyrhythmConfiguration(intent: PlaybackIntent.UpdatePolyrhythm) =
        CommittedPlaybackConfiguration.Polyrhythm(
            intent.bpm,
            intent.beats,
            intent.against,
            ownership.value.muted
        )

    private fun queueUpdate(
        sequence: Long,
        intent: PlaybackIntent,
        configuration: CommittedPlaybackConfiguration
    ) {
        val current = transportState.value as PlaybackTransportState.SessionState
        val superseded = pendingUpdates.filter {
            it.sessionId == current.context.sessionId &&
                it.configuration::class == configuration::class
        }
        pendingUpdates.removeAll(superseded.toSet())
        superseded.forEach { update ->
            recordOutcome(
                update.sequence,
                update.intent,
                rejectedOutcome(
                    update.sequence,
                    PlaybackCoordinatorFailureCode.SUPERSEDED,
                    "Playback update superseded by a newer configuration"
                )
            )
        }
        pendingUpdates.addLast(
            PendingUpdate(sequence, intent, current.context.sessionId, configuration)
        )
        dispatchNextUpdate()
    }

    private fun dispatchNextUpdate() {
        if (inFlightUpdate != null) return
        val current = transportState.value as? PlaybackTransportState.Playing ?: return
        val update = pendingUpdates.removeFirstOrNull() ?: return
        if (update.sessionId != current.context.sessionId) {
            rejectUpdate(update, PlaybackEngineUpdateResult.Reason.STALE_SESSION)
            dispatchNextUpdate()
            return
        }
        inFlightUpdate = update
        val completion: (PlaybackEngineUpdateResult) -> Unit = { result ->
            onControlContext { applyUpdateCompletion(update, result) }
        }
        try {
            when (val configuration = update.configuration) {
                is CommittedPlaybackConfiguration.Standard ->
                    engine.updateStandardSession(update.sessionId, configuration, completion)
                is CommittedPlaybackConfiguration.Polyrhythm ->
                    engine.updatePolyrhythmSession(update.sessionId, configuration, completion)
            }
        } catch (failure: RuntimeException) {
            applyUpdateCompletion(
                update,
                PlaybackEngineUpdateResult.Rejected(
                    update.sessionId,
                    PlaybackEngineUpdateResult.Reason.ENGINE_FAILURE,
                    failure.message
                )
            )
        }
    }

    private fun applyUpdateCompletion(
        update: PendingUpdate,
        result: PlaybackEngineUpdateResult
    ) {
        if (inFlightUpdate?.sequence != update.sequence) return
        inFlightUpdate = null
        val current = transportState.value as? PlaybackTransportState.Playing
        if (result.sessionId != update.sessionId || current?.context?.sessionId != update.sessionId) {
            rejectUpdate(update, PlaybackEngineUpdateResult.Reason.STALE_SESSION)
            dispatchNextUpdate()
            return
        }
        when (result) {
            is PlaybackEngineUpdateResult.Accepted -> {
                amendTransportConfiguration { update.configuration.withMuted(ownership.value.muted) }
                recordOutcome(
                    update.sequence,
                    update.intent,
                    PlaybackIntentOutcome.Accepted(update.sequence)
                )
            }
            is PlaybackEngineUpdateResult.Rejected -> rejectUpdate(
                update,
                result.reason,
                result.diagnostic
            )
        }
        dispatchNextUpdate()
    }

    private fun rejectUpdate(
        update: PendingUpdate,
        reason: PlaybackEngineUpdateResult.Reason,
        diagnostic: String? = null
    ) {
        recordOutcome(
            update.sequence,
            update.intent,
            rejectedOutcome(
                update.sequence,
                reason.coordinatorFailureCode(),
                diagnostic ?: "Playback update rejected: $reason"
            )
        )
    }

    private fun PlaybackEngineUpdateResult.Reason.coordinatorFailureCode() = when (this) {
        PlaybackEngineUpdateResult.Reason.STALE_SESSION ->
            PlaybackCoordinatorFailureCode.STALE_SESSION
        PlaybackEngineUpdateResult.Reason.INACTIVE_MODE ->
            PlaybackCoordinatorFailureCode.MODE_MISMATCH
        PlaybackEngineUpdateResult.Reason.RENDERER_REJECTED ->
            PlaybackCoordinatorFailureCode.RENDERER_REJECTED
        PlaybackEngineUpdateResult.Reason.INVALID_CONFIGURATION ->
            PlaybackCoordinatorFailureCode.INVALID_INPUT
        PlaybackEngineUpdateResult.Reason.ENGINE_FAILURE ->
            PlaybackCoordinatorFailureCode.ENGINE_FAILURE
    }

    private fun cancelUpdatesFor(sessionId: PlaybackSessionId) {
        val inFlight = inFlightUpdate
        if (inFlight?.sessionId == sessionId) {
            inFlightUpdate = null
            rejectUpdate(inFlight, PlaybackEngineUpdateResult.Reason.STALE_SESSION)
        }
        val retained = ArrayDeque<PendingUpdate>()
        while (pendingUpdates.isNotEmpty()) {
            val update = pendingUpdates.removeFirst()
            if (update.sessionId == sessionId) {
                rejectUpdate(update, PlaybackEngineUpdateResult.Reason.STALE_SESSION)
            } else {
                retained.addLast(update)
            }
        }
        pendingUpdates.addAll(retained)
    }

    private fun CommittedPlaybackConfiguration.withMuted(
        muted: Boolean
    ): CommittedPlaybackConfiguration = when (this) {
        is CommittedPlaybackConfiguration.Standard -> copy(muted = muted)
        is CommittedPlaybackConfiguration.Polyrhythm -> copy(muted = muted)
    }

    private fun validate(intent: PlaybackIntent): String? = when (intent) {
        is PlaybackIntent.Invalid -> intent.diagnostic
        is PlaybackIntent.StartStandard,
        is PlaybackIntent.ReplaceStandard,
        is PlaybackIntent.UpdateStandard -> {
            val standard = when (intent) {
                is PlaybackIntent.StartStandard -> intent
                is PlaybackIntent.ReplaceStandard -> intent.asStartIntent()
                is PlaybackIntent.UpdateStandard -> PlaybackIntent.StartStandard(
                    intent.bpm,
                    intent.subdivisions,
                    intent.accentPattern,
                    intent.alternateSixteenth
                )
            }
            when (
                val result = FramePlaybackPublicationBoundary.standardConfiguration(
                    standard.bpm,
                    standard.subdivisions,
                    standard.accentPattern,
                    standard.alternateSixteenth,
                    ownership.value.muted
                )
            ) {
                is com.bfunkstudios.beatclikr.music.PlaybackInputResult.Accepted -> null
                is com.bfunkstudios.beatclikr.music.PlaybackInputResult.Rejected ->
                    result.failure.toString()
            }
        }
        is PlaybackIntent.StartPolyrhythm,
        is PlaybackIntent.UpdatePolyrhythm -> {
            val polyrhythm = when (intent) {
                is PlaybackIntent.StartPolyrhythm -> intent
                is PlaybackIntent.UpdatePolyrhythm -> PlaybackIntent.StartPolyrhythm(
                    intent.bpm,
                    intent.beats,
                    intent.against
                )
            }
            when (
            val result = FramePlaybackPublicationBoundary.polyrhythmConfiguration(
                polyrhythm.bpm,
                polyrhythm.beats,
                polyrhythm.against,
                ownership.value.muted
            )
        ) {
            is com.bfunkstudios.beatclikr.music.PlaybackInputResult.Accepted -> null
            is com.bfunkstudios.beatclikr.music.PlaybackInputResult.Rejected ->
                result.failure.toString()
            }
        }
        is PlaybackIntent.SelectSounds ->
            if (intent.beat.resourceId == null || intent.rhythm.resourceId == null) {
                "Selected sounds have no Android resources"
            } else {
                null
            }
        is PlaybackIntent.PrepareSounds ->
            if (intent.sounds.isEmpty()) "Sound preparation set is empty" else null
        else -> null
    }

    private fun validateMode(intent: PlaybackIntent): String? = when (intent) {
        is PlaybackIntent.UpdateStandard ->
            if (transportState.value.activeModeOrNone() == PlaybackMode.STANDARD) {
                null
            } else {
                "Standard update requires an active standard session"
            }
        is PlaybackIntent.UpdatePolyrhythm ->
            if (transportState.value.activeModeOrNone() == PlaybackMode.POLYRHYTHM) {
                null
            } else {
                "Polyrhythm update requires an active polyrhythm session"
            }
        else -> null
    }

    private fun rejectedOutcome(
        sequence: Long,
        code: PlaybackCoordinatorFailureCode,
        diagnostic: String
    ): PlaybackIntentOutcome.Rejected {
        val failure = PlaybackCoordinatorFailure(code, diagnostic)
        return PlaybackIntentOutcome.Rejected(sequence, failure)
    }

    private fun recordOutcome(
        sequence: Long,
        intent: PlaybackIntent,
        outcome: PlaybackIntentOutcome
    ) {
        mutateOwnership {
            it.copy(
                lastCommandSequence = sequence,
                lastOutcome = outcome
            )
        }
        mutableControlEvents.tryEmit(
            PlaybackControlEvent.IntentCompleted(sequence, intent, outcome)
        )
    }

    private fun applyStop(
        sequence: Long,
        expectedSessionId: PlaybackSessionId? = null
    ) {
        val currentSession = transportState.value as? PlaybackTransportState.SessionState
        if (expectedSessionId != null &&
            (currentSession?.context?.sessionId != expectedSessionId || pendingReplacement != null)) {
            mutateOwnership { it.copy(lastCommandSequence = sequence) }
            return
        }
        pendingReplacement = null
        when (val current = transportState.value) {
            PlaybackTransportState.Idle -> Unit
            is PlaybackTransportState.Stopping -> Unit
            is PlaybackTransportState.Interrupted -> {
                cancelUpdatesFor(current.context.sessionId)
                engine.stopSession(current.context.sessionId, current.context.mode)
            }
            is PlaybackTransportState.Failed -> transitionTo(PlaybackTransportState.Idle)
            is PlaybackTransportState.SessionState -> {
                cancelUpdatesFor(current.context.sessionId)
                publishRenderedEvents(current.context.sessionId, detectRuntimeFailure = false)
                transitionTo(PlaybackTransportState.Stopping(current.context))
                engine.stopSession(current.context.sessionId, current.context.mode)
            }
        }
        mutateOwnership { it.copy(lastCommandSequence = sequence) }
    }

    private fun applySystemInput(input: PlaybackSystemInput) {
        when (input) {
            is PlaybackSystemInput.Interrupted -> {
                val current =
                    transportState.value as? PlaybackTransportState.SessionState ?: return
                if (current.context.sessionId != input.sessionId) return
                when (current) {
                    is PlaybackTransportState.Playing -> interrupt(current, input.reason)
                    is PlaybackTransportState.Preparing,
                    is PlaybackTransportState.Starting -> failInterruptedStart(
                        current,
                        input.reason
                    )
                    else -> Unit
                }
            }
            is PlaybackSystemInput.EngineFailed -> {
                val current = transportState.value as? PlaybackTransportState.SessionState ?: return
                if (current.context.sessionId != input.sessionId ||
                    current is PlaybackTransportState.Stopping ||
                    current is PlaybackTransportState.Interrupted ||
                    current is PlaybackTransportState.Failed) {
                    return
                }
                transitionTo(
                    PlaybackTransportState.Failed(
                        current.context,
                        PlaybackFailureReason.Engine(input.diagnostic)
                    )
                )
                mutateOwnership { it.copy(activeMode = PlaybackMode.NONE) }
                engine.stopSession(current.context.sessionId, current.context.mode)
            }
        }
    }

    private fun interrupt(
        current: PlaybackTransportState.Playing,
        reason: PlaybackInterruptionReason
    ) {
        publishRenderedEvents(current.context.sessionId, detectRuntimeFailure = false)
        transitionTo(PlaybackTransportState.Interrupted(current.context, reason))
        mutateOwnership { it.copy(activeMode = PlaybackMode.NONE) }
        engine.stopSession(current.context.sessionId, current.context.mode)
    }

    private fun failInterruptedStart(
        current: PlaybackTransportState.SessionState,
        reason: PlaybackInterruptionReason
    ) {
        val failure = when (reason) {
            PlaybackInterruptionReason.AudioFocusLost ->
                PlaybackFailureReason.AudioFocusUnavailable
            is PlaybackInterruptionReason.RouteUnavailable ->
                PlaybackFailureReason.RouteUnavailable
            else -> PlaybackFailureReason.Engine("Playback interrupted during startup: $reason")
        }
        transitionTo(PlaybackTransportState.Failed(current.context, failure))
        mutateOwnership { it.copy(activeMode = PlaybackMode.NONE) }
        engine.stopSession(current.context.sessionId, current.context.mode)
    }

    private fun replaceOrStart(start: PendingStart) {
        when (val current = transportState.value) {
            PlaybackTransportState.Idle -> beginStart(start)
            is PlaybackTransportState.Interrupted,
            is PlaybackTransportState.Failed -> {
                transitionTo(PlaybackTransportState.Idle)
                beginStart(start)
            }
            is PlaybackTransportState.Stopping -> pendingReplacement = start
            is PlaybackTransportState.SessionState -> {
                if (current is PlaybackTransportState.Playing) {
                    publishRenderedEvents(
                        current.context.sessionId,
                        detectRuntimeFailure = false
                    )
                }
                cancelUpdatesFor(current.context.sessionId)
                pendingReplacement = start
                transitionTo(PlaybackTransportState.Stopping(current.context))
                engine.stopSession(current.context.sessionId, current.context.mode)
            }
        }
    }

    private fun beginStart(start: PendingStart) {
        val requested = start.context()
        transitionTo(
            PlaybackTransportState.Preparing(requested)
        )
        val sounds = engine.activeSoundConfiguration()
        if (sounds == null) {
            transitionTo(
                PlaybackTransportState.Failed(
                    requested,
                    PlaybackFailureReason.Engine("Prepared sounds are unavailable")
                )
            )
            mutateOwnership { it.copy(activeMode = PlaybackMode.NONE) }
            return
        }
        val prepared = requested.copy(audibleSounds = sounds)
        transitionTo(
            PlaybackTransportState.Preparing(prepared)
        )
        transitionTo(PlaybackTransportState.Starting(prepared))
        when (start) {
            is PendingStart.Standard -> engine.beginStandardSession(
                start.sessionId,
                start.intent.bpm,
                start.intent.subdivisions,
                start.intent.accentPattern,
                start.intent.alternateSixteenth
            )
            is PendingStart.Polyrhythm -> engine.beginPolyrhythmSession(
                start.sessionId,
                start.intent.bpm,
                start.intent.beats,
                start.intent.against
            )
        }
    }

    private fun applyEngineStarted(evidence: PlaybackEngineStartEvidence) {
        val current = transportState.value as? PlaybackTransportState.Starting ?: return
        if (current.context.sessionId != evidence.sessionId) return
        if (evidence.route == AudioOutputRoute.UNKNOWN) {
            transitionTo(
                PlaybackTransportState.Failed(
                    current.context,
                    PlaybackFailureReason.RouteUnavailable
                )
            )
            mutateOwnership { it.copy(activeMode = PlaybackMode.NONE) }
            engine.stopSession(current.context.sessionId, current.context.mode)
            return
        }
        val committed = current.context.copy(
            audibleSounds = evidence.audibleSounds,
            route = evidence.route,
            backend = evidence.backend
        )
        lastBackendFailure = null
        mutableCommittedEvents.tryEmit(
            PlaybackCommittedEvent.FirstEventScheduled(
                nextCommittedEventSequence++,
                evidence.sessionId,
                evidence.firstEventFrame
            )
        )
        transitionTo(PlaybackTransportState.Playing(committed))
        publishRenderedEvents()
        mutateOwnership {
            it.copy(activeMode = committed.mode, audibleSounds = evidence.audibleSounds)
        }
        dispatchNextUpdate()
    }

    private fun applyAudioFocusUnavailable(sessionId: PlaybackSessionId) {
        val current = transportState.value as? PlaybackTransportState.Starting ?: return
        if (current.context.sessionId != sessionId) return
        transitionTo(
            PlaybackTransportState.Failed(
                current.context,
                PlaybackFailureReason.AudioFocusUnavailable
            )
        )
        mutateOwnership { it.copy(activeMode = PlaybackMode.NONE) }
        engine.stopSession(current.context.sessionId, current.context.mode)
    }

    private fun publishRenderedEvents() {
        val current = transportState.value as? PlaybackTransportState.Playing ?: return
        publishRenderedEvents(current.context.sessionId, detectRuntimeFailure = true)
    }

    private fun publishRenderedEvents(
        sessionId: PlaybackSessionId,
        detectRuntimeFailure: Boolean
    ) {
        val batch = engine.drainRenderedEvents(nextEngineCaptureSequence) ?: return
        nextEngineCaptureSequence = batch.events.nextCaptureSequence
        if (batch.events.droppedRecords > 0) {
            mutableCommittedEvents.tryEmit(
                PlaybackCommittedEvent.RecordsDropped(
                    nextCommittedEventSequence++,
                    batch.events.droppedRecords
                )
            )
        }
        batch.events.records.forEach { record ->
            if (record.sessionId != sessionId.value) return@forEach
            mutableCommittedEvents.tryEmit(
                PlaybackCommittedEvent.Rendered(
                    nextCommittedEventSequence++,
                    sessionId,
                    record.eventSequence,
                    record.role,
                    record.intendedFrame,
                    record.muted,
                    presentationFor(record.intendedFrame, batch),
                    record.roleIndex
                )
            )
        }
        if (detectRuntimeFailure) {
            val current = transportState.value as? PlaybackTransportState.Playing ?: return
            if (current.context.sessionId == sessionId) publishRuntimeFailure(current)
        }
    }

    private fun publishRuntimeFailure(current: PlaybackTransportState.Playing) {
        val failure = engine.getFrameAudioMetricsSnapshot()
            ?.latestBackendFailure
            ?.takeIf {
                it.code == AudioBackendFailureCode.WRITE_FAILED ||
                    it.code == AudioBackendFailureCode.DEVICE_DISCONNECTED ||
                    it.code == AudioBackendFailureCode.INTERNAL_ERROR
            }
            ?: return
        if (failure == lastBackendFailure) return
        lastBackendFailure = failure
        applySystemInput(
            PlaybackSystemInput.EngineFailed(
                current.context.sessionId,
                "${failure.operation}: ${failure.code}"
            )
        )
    }

    private fun presentationFor(
        intendedFrame: Long,
        batch: FrameAudioRenderedEventBatch
    ): EventPresentation {
        val correlation = batch.correlation ?: return EventPresentation.Unavailable
        return try {
            val frameDelta = Math.subtractExact(intendedFrame, correlation.presentedFrame)
            val wholeSeconds = Math.floorDiv(frameDelta, batch.sampleRate.toLong())
            val remainderFrames = Math.floorMod(frameDelta, batch.sampleRate.toLong())
            val deltaNanos = Math.addExact(
                Math.multiplyExact(wholeSeconds, NANOS_PER_SECOND),
                Math.multiplyExact(remainderFrames, NANOS_PER_SECOND) / batch.sampleRate
            )
            EventPresentation.Correlated(
                Math.addExact(correlation.presentationNanoTime, deltaNanos),
                correlation
            )
        } catch (_: ArithmeticException) {
            EventPresentation.Unavailable
        }
    }

    private fun applyEngineStartFailed(
        sessionId: PlaybackSessionId,
        diagnostic: String
    ) {
        val current = transportState.value as? PlaybackTransportState.Starting ?: return
        if (current.context.sessionId != sessionId) return
        transitionTo(
            PlaybackTransportState.Failed(
                current.context,
                PlaybackFailureReason.StreamStart(diagnostic)
            )
        )
        mutateOwnership { it.copy(activeMode = PlaybackMode.NONE) }
        engine.stopSession(current.context.sessionId, current.context.mode)
    }

    private fun applyEngineStopped(sessionId: PlaybackSessionId) {
        val current = transportState.value as? PlaybackTransportState.SessionState ?: return
        if (current.context.sessionId != sessionId) return
        publishRenderedEvents(sessionId, detectRuntimeFailure = false)
        if (current is PlaybackTransportState.Interrupted) {
            transitionTo(PlaybackTransportState.Idle)
            return
        }
        if (current !is PlaybackTransportState.Stopping) return
        mutateOwnership { it.copy(activeMode = PlaybackMode.NONE) }
        val replacement = pendingReplacement
        if (replacement == null) {
            transitionTo(PlaybackTransportState.Idle)
        } else {
            try {
                beginStart(replacement)
            } finally {
                pendingReplacement = null
            }
        }
    }

    private fun amendTransportConfiguration(
        transform: (CommittedPlaybackConfiguration) -> CommittedPlaybackConfiguration
    ) {
        val current = transportState.value as? PlaybackTransportState.SessionState ?: return
        val amended = current.context.copy(
            configuration = transform(current.context.configuration)
        )
        val next = when (current) {
            is PlaybackTransportState.Preparing -> current.copy(context = amended)
            is PlaybackTransportState.Starting -> current.copy(context = amended)
            is PlaybackTransportState.Playing -> current.copy(context = amended)
            is PlaybackTransportState.Stopping,
            is PlaybackTransportState.Interrupted,
            is PlaybackTransportState.Failed -> return
        }
        transitionTo(next)
    }

    private fun transitionTo(next: PlaybackTransportState) {
        val previous = mutableTransportState.value
        if (previous is PlaybackTransportState.Stopping) {
            check(
                pendingReplacement == null && next is PlaybackTransportState.Idle ||
                    pendingReplacement != null && next is PlaybackTransportState.Preparing
            ) {
                "Stopping must resolve its pending replacement"
            }
        }
        PlaybackTransportTransitions.requireLegal(previous, next)
        if (previous is PlaybackTransportState.SessionState &&
            (next !is PlaybackTransportState.SessionState ||
                next.context.sessionId != previous.context.sessionId ||
                next is PlaybackTransportState.Stopping ||
                next is PlaybackTransportState.Interrupted ||
                next is PlaybackTransportState.Failed)) {
            cancelUpdatesFor(previous.context.sessionId)
        }
        if (previous is PlaybackTransportState.Playing &&
            next !is PlaybackTransportState.Playing) {
            eventDrainFuture?.cancel(false)
            eventDrainFuture = null
        }
        mutableTransportState.value = next
        mutateOwnership {
            it.copy(
                activeMode = (next as? PlaybackTransportState.Playing)
                    ?.context
                    ?.mode
                    ?: PlaybackMode.NONE
            )
        }
        val transition = PlaybackStateTransition(nextTransitionSequence++, previous, next)
        mutableCommittedEvents.tryEmit(
            PlaybackCommittedEvent.StateChanged(
                nextCommittedEventSequence++,
                transition
            )
        )
        mutableStateTransitions.tryEmit(transition)
        if (previous !is PlaybackTransportState.Playing &&
            next is PlaybackTransportState.Playing &&
            !released) {
            eventDrainFuture = eventDrainScheduler.scheduleAtFixedRate(
                { onControlContext(::publishRenderedEvents) },
                0,
                EVENT_DRAIN_PERIOD_MILLIS,
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun newSessionId(): PlaybackSessionId =
        PlaybackSessionId(nextSessionId).also { nextSessionId = Math.incrementExact(nextSessionId) }

    private fun onControlContext(operation: () -> Unit) {
        if (Thread.currentThread() === controlThread) {
            runControlSafely("Playback callback failed", operation)
        } else if (!released) {
            executeControl("Playback callback failed", operation)
        }
    }

    private fun executeControl(diagnostic: String, operation: () -> Unit) {
        try {
            executor.execute {
                controlThread = Thread.currentThread()
                runControlSafely(diagnostic, operation)
            }
        } catch (_: RejectedExecutionException) {
            if (!released) {
                mutableControlEvents.tryEmit(
                    PlaybackControlEvent.UnexpectedFailure(
                        "$diagnostic: control executor rejected work"
                    )
                )
            }
        }
    }

    private fun runControlSafely(diagnostic: String, operation: () -> Unit) {
        try {
            operation()
        } catch (failure: RuntimeException) {
            val detail = failure.message?.let { "$diagnostic: $it" } ?: diagnostic
            mutableControlEvents.tryEmit(PlaybackControlEvent.UnexpectedFailure(detail))
            handleTransportFailure(detail)
        }
    }

    private fun handleTransportFailure(diagnostic: String) {
        val current = transportState.value
        if (current is PlaybackTransportState.Preparing ||
            current is PlaybackTransportState.Starting ||
            current is PlaybackTransportState.Playing) {
            transitionTo(
                PlaybackTransportState.Failed(
                    current.context,
                    PlaybackFailureReason.Engine(diagnostic)
                )
            )
        }
    }

    private fun settleReleasedTransport() {
        val current = transportState.value
        if (current is PlaybackTransportState.Preparing ||
            current is PlaybackTransportState.Starting ||
            current is PlaybackTransportState.Playing) {
            transitionTo(
                PlaybackTransportState.Failed(
                    current.context,
                    PlaybackFailureReason.Engine("Playback coordinator released")
                )
            )
        }
        if (transportState.value is PlaybackTransportState.SessionState) {
            transitionTo(PlaybackTransportState.Idle)
        }
    }

    private fun PlaybackTransportState.activeModeOrNone(): PlaybackMode = when (this) {
        is PlaybackTransportState.Preparing,
        is PlaybackTransportState.Starting,
        is PlaybackTransportState.Playing -> context.mode
        else -> PlaybackMode.NONE
    }

    private sealed interface PendingStart {
        val sessionId: PlaybackSessionId
        val muted: Boolean

        fun context(): PlaybackSessionContext

        data class Standard(
            override val sessionId: PlaybackSessionId,
            val intent: PlaybackIntent.StartStandard,
            override val muted: Boolean
        ) : PendingStart {
            override fun context() = PlaybackSessionContext(
                sessionId,
                PlaybackMode.STANDARD,
                CommittedPlaybackConfiguration.Standard(
                    intent.bpm,
                    intent.subdivisions,
                    intent.accentPattern?.toList(),
                    intent.alternateSixteenth,
                    muted
                ),
                startOrigin = PlaybackStartOrigin.USER
            )
        }

        data class Polyrhythm(
            override val sessionId: PlaybackSessionId,
            val intent: PlaybackIntent.StartPolyrhythm,
            override val muted: Boolean
        ) : PendingStart {
            override fun context() = PlaybackSessionContext(
                sessionId,
                PlaybackMode.POLYRHYTHM,
                CommittedPlaybackConfiguration.Polyrhythm(
                    intent.bpm,
                    intent.beats,
                    intent.against,
                    muted
                ),
                startOrigin = PlaybackStartOrigin.USER
            )
        }
    }

    private data class PendingUpdate(
        val sequence: Long,
        val intent: PlaybackIntent,
        val sessionId: PlaybackSessionId,
        val configuration: CommittedPlaybackConfiguration
    )

    private fun updateRequestedSounds(
        bank: SoundBank? = null,
        beat: SoundFile? = null,
        rhythm: SoundFile? = null
    ) {
        mutateOwnership { current ->
            current.copy(
                requestedSounds = current.requestedSounds.copy(
                    bank = bank ?: current.requestedSounds.bank,
                    beatSound = beat ?: current.requestedSounds.beatSound,
                    rhythmSound = rhythm ?: current.requestedSounds.rhythmSound
                )
            )
        }
    }

    @Synchronized
    private fun onSoundPreparation(publication: SoundPreparationPublication) {
        if (released) return
        executeControl("Sound preparation callback failed") {
            if (publication.requestSequence != null &&
                publication.requestSequence < latestSoundRequestSequence) {
                return@executeControl
            }
            val current = transportState.value as? PlaybackTransportState.Playing
            if (publication.sessionId != null &&
                publication.sessionId != current?.context?.sessionId) {
                return@executeControl
            }
            val active = publication.active
            val failure = publication.failure
            val requested = ownership.value.requestedSounds
            val matches = active != null &&
                active.bank == requested.bank &&
                active.beatSound == requested.beatSound &&
                active.rhythmSound == requested.rhythmSound
            val adopted = matches &&
                publication.adopted &&
                publication.sessionId == current?.context?.sessionId
            mutateOwnership {
                it.copy(
                    audibleSounds = if (adopted) active else it.audibleSounds,
                    soundPreparationFailure = failure
                )
            }
            if (adopted) {
                val adoptedSounds = requireNotNull(active)
                val adoptedState = requireNotNull(current)
                transitionTo(
                    adoptedState.copy(
                        context = adoptedState.context.copy(audibleSounds = adoptedSounds)
                    )
                )
            }
            if (failure != null) {
                publication.requestSequence?.let { requestSequence ->
                    mutableControlEvents.tryEmit(
                        PlaybackControlEvent.SoundPreparationFailed(
                            requestSequence,
                            failure
                        )
                    )
                }
            }
        }
    }

    private fun refreshAudibleSounds() {
        onSoundPreparation(
            SoundPreparationPublication(
                null,
                null,
                false,
                engine.activeSoundConfiguration(),
                engine.soundPreparationFailure()
            )
        )
    }

    private fun PlaybackIntent.immutableCopy(): PlaybackIntent = when (this) {
        is PlaybackIntent.StartStandard -> copy(accentPattern = accentPattern?.toList())
        is PlaybackIntent.ReplaceStandard -> copy(accentPattern = accentPattern?.toList())
        is PlaybackIntent.UpdateStandard -> copy(accentPattern = accentPattern?.toList())
        is PlaybackIntent.PrepareSounds -> copy(sounds = sounds.toList())
        else -> this
    }

    private fun PlaybackIntent.ReplaceStandard.asStartIntent() =
        PlaybackIntent.StartStandard(
            bpm,
            subdivisions,
            accentPattern?.toList(),
            alternateSixteenth
        )

    private inline fun mutateOwnership(
        transform: (PlaybackOwnershipSnapshot) -> PlaybackOwnershipSnapshot
    ) {
        mutableOwnership.value = transform(mutableOwnership.value)
    }

    private companion object {
        const val TIMING_EVENT_CAPACITY = 64
        const val CONTROL_EVENT_CAPACITY = 64
        const val TRANSPORT_EVENT_CAPACITY = 64
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val EVENT_DRAIN_PERIOD_MILLIS = 10L
    }
}
