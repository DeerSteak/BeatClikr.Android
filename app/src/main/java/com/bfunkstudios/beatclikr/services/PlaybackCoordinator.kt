package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.music.MusicalEventRole
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.time.ZoneId
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
    val muted: Boolean = false,
    val requestedSounds: RequestedSoundConfiguration = RequestedSoundConfiguration(
        SoundBank.ACOUSTIC,
        SoundFile.CLICK_HI,
        SoundFile.CLICK_LO
    ),
    val audibleSounds: ActiveSoundConfiguration? = null,
    val soundPreparationFailure: SoundPreparationFailure? = null
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
        val alternateSixteenth: Boolean,
        val practiceItem: PracticeItemSnapshot = PracticeItemSnapshot.metronome()
    ) : PlaybackIntent
    data class ReplaceStandard(
        val bpm: Float,
        val subdivisions: Int,
        val accentPattern: List<Boolean>?,
        val alternateSixteenth: Boolean,
        val practiceItem: PracticeItemSnapshot = PracticeItemSnapshot.metronome()
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
        val against: Int,
        val practiceItem: PracticeItemSnapshot = PracticeItemSnapshot.polyrhythm()
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
    val to: PlaybackTransportState,
    val occurredAtElapsedNanos: Long,
    val occurredAtWallMillis: Long,
    val timeZoneIdentifier: String
)

sealed interface PlaybackCommittedEvent {
    val sequence: Long

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
        configuration: ValidatedStandardConfiguration
    )
    fun beginPolyrhythmSession(
        sessionId: PlaybackSessionId,
        configuration: ValidatedPolyrhythmConfiguration
    )
    fun updateStandardSession(
        sessionId: PlaybackSessionId,
        configuration: ValidatedStandardConfiguration,
        completion: (PlaybackEngineUpdateResult) -> Unit
    )
    fun updatePolyrhythmSession(
        sessionId: PlaybackSessionId,
        configuration: ValidatedPolyrhythmConfiguration,
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
        val reason: PlaybackCoordinatorFailureCode,
        val diagnostic: String? = null
    ) : PlaybackEngineUpdateResult
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
        },
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val timeZoneIdentifier: () -> String = { ZoneId.systemDefault().id }
) : IAudioPlayerService, MetronomeAudioEngineDelegate, PolyrhythmAudioEngineDelegate,
    PlaybackEngineTransportObserver, PlaybackObservation, PlaybackLifecycleObservation {
    private val mutableOwnership = MutableStateFlow(PlaybackOwnershipSnapshot())
    private val mutableControlEvents = MutableSharedFlow<PlaybackControlEvent>(
        replay = CONTROL_EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val mutableTransportState =
        MutableStateFlow<PlaybackTransportState>(PlaybackTransportState.Idle)
    private val lifecycleJournalLock = Any()
    private val lifecycleJournal = ArrayDeque<PlaybackStateTransition>()
    private var acknowledgedLifecycleSequence = 0L
    private val mutableLifecycleCheckpoint = MutableStateFlow(
        PlaybackLifecycleCheckpoint(0, PlaybackTransportState.Idle)
    )
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
    val controlEvents: SharedFlow<PlaybackControlEvent> = mutableControlEvents
    override val transportState: StateFlow<PlaybackTransportState> = mutableTransportState
    override val lifecycleCheckpoint: StateFlow<PlaybackLifecycleCheckpoint> =
        mutableLifecycleCheckpoint
    val stateTransitions: SharedFlow<PlaybackStateTransition> = mutableStateTransitions
    override val committedEvents: SharedFlow<PlaybackCommittedEvent> = mutableCommittedEvents

    override fun recentLifecycleDiagnostics(limit: Int): List<PlaybackLifecycleDiagnostic> =
        mutableStateTransitions.replayCache.takeLast(limit.coerceIn(0, TRANSPORT_EVENT_CAPACITY))
            .map { transition ->
                PlaybackLifecycleDiagnostic(
                    transition.sequence,
                    transition.from.diagnosticName,
                    transition.to.diagnosticName
                )
            }

    override fun lifecycleTransitionsAfter(sequence: Long): PlaybackLifecycleBatch {
        require(sequence >= 0) { "Lifecycle sequence must not be negative" }
        return synchronized(lifecycleJournalLock) {
            require(sequence >= acknowledgedLifecycleSequence) {
                "Lifecycle sequence $sequence was already acknowledged"
            }
            val oldestAvailable = lifecycleJournal.firstOrNull()?.sequence
                ?: mutableLifecycleCheckpoint.value.latestTransitionSequence + 1
            PlaybackLifecycleBatch(
                lifecycleJournal.filter { it.sequence > sequence },
                mutableLifecycleCheckpoint.value,
                if (sequence + 1 < oldestAvailable) {
                    PlaybackLifecycleGap(sequence, oldestAvailable)
                } else {
                    null
                }
            )
        }
    }

    override fun acknowledgeLifecycleTransitionsThrough(sequence: Long) {
        require(sequence >= 0) { "Lifecycle sequence must not be negative" }
        synchronized(lifecycleJournalLock) {
            require(sequence <= mutableLifecycleCheckpoint.value.latestTransitionSequence) {
                "Cannot acknowledge an unpublished lifecycle sequence"
            }
            if (sequence <= acknowledgedLifecycleSequence) return
            while (lifecycleJournal.firstOrNull()?.sequence?.let { it <= sequence } == true) {
                lifecycleJournal.removeFirst()
            }
            acknowledgedLifecycleSequence = sequence
        }
    }

    init {
        engine.soundPreparationObserver = ::onSoundPreparation
        engine.transportObserver = this
        engine.delegate = this
        engine.polyrhythmDelegate = this
        refreshAudibleSounds()
    }

    @Synchronized
    override fun submit(intent: PlaybackIntent): Long {
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
    }

    override fun metronomeBeatFired(
        isBeat: Boolean,
        beatInterval: Float,
        beatTimeNanos: Long
    ) {
        onControlContext(::publishRenderedEvents)
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
        val validation = validate(intent)
        if (validation is IntentValidation.Rejected) {
            recordOutcome(
                sequence,
                intent,
                rejectedOutcome(
                    sequence,
                    PlaybackCoordinatorFailureCode.INVALID_INPUT,
                    validation.diagnostic
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
                    val configuration = requireNotNull((validation as IntentValidation.Accepted).configuration)
                        as ValidatedStandardConfiguration
                    if (transportState.value.activeModeOrNone() == PlaybackMode.STANDARD) {
                        queueUpdate(sequence, intent, configuration)
                        awaitsEngineAcknowledgement = true
                    } else {
                        replaceOrStart(
                            PendingStart.Standard(
                                newSessionId(),
                                intent,
                                configuration
                            )
                        )
                    }
                }
                is PlaybackIntent.ReplaceStandard -> replaceOrStart(
                    PendingStart.Standard(
                        newSessionId(),
                        intent.asStartIntent(),
                        requireNotNull((validation as IntentValidation.Accepted).configuration)
                            as ValidatedStandardConfiguration
                    )
                )
                is PlaybackIntent.UpdateStandard -> {
                    queueUpdate(
                        sequence,
                        intent,
                        requireNotNull((validation as IntentValidation.Accepted).configuration)
                    )
                    awaitsEngineAcknowledgement = true
                }
                is PlaybackIntent.StartPolyrhythm -> {
                    val configuration = requireNotNull((validation as IntentValidation.Accepted).configuration)
                        as ValidatedPolyrhythmConfiguration
                    if (transportState.value.activeModeOrNone() == PlaybackMode.POLYRHYTHM) {
                        queueUpdate(sequence, intent, configuration)
                        awaitsEngineAcknowledgement = true
                    } else {
                        replaceOrStart(
                            PendingStart.Polyrhythm(
                                newSessionId(),
                                intent,
                                configuration
                            )
                        )
                    }
                }
                is PlaybackIntent.UpdatePolyrhythm -> {
                    queueUpdate(
                        sequence,
                        intent,
                        requireNotNull((validation as IntentValidation.Accepted).configuration)
                    )
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

    private fun queueUpdate(
        sequence: Long,
        intent: PlaybackIntent,
        configuration: ValidatedPlaybackConfiguration
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
            rejectUpdate(update, PlaybackCoordinatorFailureCode.STALE_SESSION)
            dispatchNextUpdate()
            return
        }
        inFlightUpdate = update
        val completion: (PlaybackEngineUpdateResult) -> Unit = { result ->
            onControlContext { applyUpdateCompletion(update, result) }
        }
        try {
            when (val configuration = update.configuration) {
                is ValidatedStandardConfiguration ->
                    engine.updateStandardSession(update.sessionId, configuration, completion)
                is ValidatedPolyrhythmConfiguration ->
                    engine.updatePolyrhythmSession(update.sessionId, configuration, completion)
            }
        } catch (failure: RuntimeException) {
            applyUpdateCompletion(
                update,
                PlaybackEngineUpdateResult.Rejected(
                    update.sessionId,
                    PlaybackCoordinatorFailureCode.ENGINE_FAILURE,
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
            rejectUpdate(update, PlaybackCoordinatorFailureCode.STALE_SESSION)
            dispatchNextUpdate()
            return
        }
        when (result) {
            is PlaybackEngineUpdateResult.Accepted -> {
                amendTransportConfiguration {
                    update.configuration.committed.withMuted(ownership.value.muted)
                }
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
        reason: PlaybackCoordinatorFailureCode,
        diagnostic: String? = null
    ) {
        recordOutcome(
            update.sequence,
            update.intent,
            rejectedOutcome(
                update.sequence,
                reason,
                diagnostic ?: "Playback update rejected: $reason"
            )
        )
    }

    private fun cancelUpdatesFor(sessionId: PlaybackSessionId) {
        val inFlight = inFlightUpdate
        if (inFlight?.sessionId == sessionId) {
            inFlightUpdate = null
            rejectUpdate(inFlight, PlaybackCoordinatorFailureCode.STALE_SESSION)
        }
        val retained = ArrayDeque<PendingUpdate>()
        while (pendingUpdates.isNotEmpty()) {
            val update = pendingUpdates.removeFirst()
            if (update.sessionId == sessionId) {
                rejectUpdate(update, PlaybackCoordinatorFailureCode.STALE_SESSION)
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

    private fun validate(intent: PlaybackIntent): IntentValidation = when (intent) {
        is PlaybackIntent.Invalid -> IntentValidation.Rejected(intent.diagnostic)
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
                is com.bfunkstudios.beatclikr.music.PlaybackInputResult.Accepted ->
                    IntentValidation.Accepted(result.value)
                is com.bfunkstudios.beatclikr.music.PlaybackInputResult.Rejected ->
                    IntentValidation.Rejected(result.failure.diagnostic)
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
            is com.bfunkstudios.beatclikr.music.PlaybackInputResult.Accepted ->
                IntentValidation.Accepted(result.value)
            is com.bfunkstudios.beatclikr.music.PlaybackInputResult.Rejected ->
                IntentValidation.Rejected(result.failure.diagnostic)
            }
        }
        is PlaybackIntent.SelectSounds ->
            if (intent.beat.resourceId == null || intent.rhythm.resourceId == null) {
                IntentValidation.Rejected("Selected sounds have no Android resources")
            } else {
                IntentValidation.Accepted()
            }
        is PlaybackIntent.PrepareSounds ->
            if (intent.sounds.isEmpty()) {
                IntentValidation.Rejected("Sound preparation set is empty")
            } else {
                IntentValidation.Accepted()
            }
        else -> IntentValidation.Accepted()
    }

    private val com.bfunkstudios.beatclikr.music.PlaybackInputFailure.diagnostic: String
        get() = when (this) {
            is com.bfunkstudios.beatclikr.music.PlaybackInputFailure.InvalidDomainInput -> diagnostic
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
                start.configuration
            )
            is PendingStart.Polyrhythm -> engine.beginPolyrhythmSession(
                start.sessionId,
                start.configuration
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
        mutateOwnership { it.copy(audibleSounds = evidence.audibleSounds) }
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
        val transition = PlaybackStateTransition(
            nextTransitionSequence++,
            previous,
            next,
            monotonicNanos(),
            wallClockMillis(),
            timeZoneIdentifier()
        )
        recordLifecycleTransition(transition)
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

    private fun recordLifecycleTransition(transition: PlaybackStateTransition) {
        synchronized(lifecycleJournalLock) {
            lifecycleJournal += transition
            if (lifecycleJournal.size > LIFECYCLE_JOURNAL_CAPACITY) {
                lifecycleJournal.removeFirst()
            }
            mutableLifecycleCheckpoint.value = PlaybackLifecycleCheckpoint(
                transition.sequence,
                transition.to
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
        val configuration: ValidatedPlaybackConfiguration

        fun context(): PlaybackSessionContext

        data class Standard(
            override val sessionId: PlaybackSessionId,
            val intent: PlaybackIntent.StartStandard,
            override val configuration: ValidatedStandardConfiguration
        ) : PendingStart {
            override fun context() = PlaybackSessionContext(
                sessionId,
                PlaybackMode.STANDARD,
                configuration.committed,
                startOrigin = PlaybackStartOrigin.USER,
                practiceItem = intent.practiceItem
            )
        }

        data class Polyrhythm(
            override val sessionId: PlaybackSessionId,
            val intent: PlaybackIntent.StartPolyrhythm,
            override val configuration: ValidatedPolyrhythmConfiguration
        ) : PendingStart {
            override fun context() = PlaybackSessionContext(
                sessionId,
                PlaybackMode.POLYRHYTHM,
                configuration.committed,
                startOrigin = PlaybackStartOrigin.USER,
                practiceItem = intent.practiceItem
            )
        }
    }

    private data class PendingUpdate(
        val sequence: Long,
        val intent: PlaybackIntent,
        val sessionId: PlaybackSessionId,
        val configuration: ValidatedPlaybackConfiguration
    )

    private sealed interface IntentValidation {
        data class Accepted(
            val configuration: ValidatedPlaybackConfiguration? = null
        ) : IntentValidation

        data class Rejected(val diagnostic: String) : IntentValidation
    }

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
                        context = adoptedState.context.copy(
                            audibleSounds = adoptedSounds,
                            soundPreparationFailure = null
                        )
                    )
                )
            } else if (failure != null && current != null) {
                transitionTo(
                    current.copy(context = current.context.copy(soundPreparationFailure = failure))
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
            alternateSixteenth,
            practiceItem
        )

    private inline fun mutateOwnership(
        transform: (PlaybackOwnershipSnapshot) -> PlaybackOwnershipSnapshot
    ) {
        mutableOwnership.value = transform(mutableOwnership.value)
    }

    private companion object {
        const val LIFECYCLE_JOURNAL_CAPACITY = 4_096
        const val CONTROL_EVENT_CAPACITY = 64
        const val TRANSPORT_EVENT_CAPACITY = 64
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val EVENT_DRAIN_PERIOD_MILLIS = 10L
    }
}
