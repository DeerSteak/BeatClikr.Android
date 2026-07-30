package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    data object Stop : PlaybackIntent
    data object Prewarm : PlaybackIntent
    data class PrepareSounds(val sounds: Collection<SoundFile>) : PlaybackIntent
}

enum class PlaybackCoordinatorFailureCode {
    INVALID_INPUT,
    SOUND_PREPARATION_FAILED,
    MODE_MISMATCH,
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
}

interface PlaybackEnginePort : IAudioPlayerService {
    var soundPreparationObserver:
        ((ActiveSoundConfiguration?, SoundPreparationFailure?) -> Unit)?

    fun activeSoundConfiguration(): ActiveSoundConfiguration?
    fun soundPreparationFailure(): SoundPreparationFailure?
}

class PlaybackCoordinator(
    private val engine: PlaybackEnginePort,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PlaybackCoordinatorControl")
    }
) : IAudioPlayerService, MetronomeAudioEngineDelegate, PolyrhythmAudioEngineDelegate {
    private val mutableOwnership = MutableStateFlow(PlaybackOwnershipSnapshot())
    private val mutableTimingEvents = MutableSharedFlow<PlaybackTimingEvent>(
        extraBufferCapacity = TIMING_EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val mutableControlEvents = MutableSharedFlow<PlaybackControlEvent>(
        replay = CONTROL_EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var nextCommandSequence = 1L
    @Volatile
    private var released = false

    val ownership: StateFlow<PlaybackOwnershipSnapshot> = mutableOwnership
    val timingEvents: SharedFlow<PlaybackTimingEvent> = mutableTimingEvents
    val controlEvents: SharedFlow<PlaybackControlEvent> = mutableControlEvents

    @Volatile
    override var delegate: MetronomeAudioEngineDelegate? = null

    @Volatile
    override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate? = null

    init {
        engine.soundPreparationObserver = ::onSoundPreparation
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
            executor.execute { applyIntent(sequence, publishedIntent) }
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

    override fun stopMetronome() {
        submit(PlaybackIntent.Stop)
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

    override fun stopPolyrhythm() {
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
        executor.execute {
            applyStop(sequence)
            engine.soundPreparationObserver = null
            engine.delegate = null
            engine.polyrhythmDelegate = null
            engine.release()
        }
        executor.shutdown()
        delegate = null
        polyrhythmDelegate = null
    }

    override fun metronomeBeatFired(
        isBeat: Boolean,
        beatInterval: Float,
        beatTimeNanos: Long
    ) {
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
        val outcome = try {
            when (intent) {
                is PlaybackIntent.Invalid -> error("Invalid intent passed validation")
                is PlaybackIntent.SelectSounds -> {
                    updateRequestedSounds(beat = intent.beat, rhythm = intent.rhythm)
                    engine.setupAudioPlayer(
                        requireNotNull(intent.beat.resourceId),
                        requireNotNull(intent.rhythm.resourceId)
                    )
                }
                is PlaybackIntent.SelectSoundBank -> {
                    updateRequestedSounds(bank = intent.bank)
                    engine.soundBank = intent.bank
                }
                is PlaybackIntent.SetMuted -> {
                    engine.isMuted = intent.muted
                    mutateOwnership { it.copy(muted = intent.muted) }
                }
                is PlaybackIntent.StartStandard -> {
                    if (ownership.value.activeMode == PlaybackMode.STANDARD) {
                        engine.updateTempo(
                            intent.bpm,
                            intent.subdivisions,
                            intent.accentPattern,
                            intent.alternateSixteenth
                        )
                    } else {
                        engine.stopPolyrhythm()
                        engine.startMetronome(
                            intent.bpm,
                            intent.subdivisions,
                            intent.accentPattern,
                            intent.alternateSixteenth
                        )
                    }
                    mutateOwnership { it.copy(activeMode = PlaybackMode.STANDARD) }
                }
                is PlaybackIntent.UpdateStandard -> engine.updateTempo(
                    intent.bpm,
                    intent.subdivisions,
                    intent.accentPattern,
                    intent.alternateSixteenth
                )
                is PlaybackIntent.StartPolyrhythm -> {
                    if (ownership.value.activeMode != PlaybackMode.POLYRHYTHM) {
                        engine.stopMetronome()
                    }
                    engine.startPolyrhythm(intent.bpm, intent.beats, intent.against)
                    mutateOwnership { it.copy(activeMode = PlaybackMode.POLYRHYTHM) }
                }
                is PlaybackIntent.UpdatePolyrhythm ->
                    engine.startPolyrhythm(intent.bpm, intent.beats, intent.against)
                PlaybackIntent.Stop -> {
                    applyStop(sequence)
                }
                PlaybackIntent.Prewarm -> engine.prewarmAudioTrack()
                is PlaybackIntent.PrepareSounds ->
                    engine.prepareAudioTrackSounds(intent.sounds)
            }
            PlaybackIntentOutcome.Accepted(sequence)
        } catch (failure: RuntimeException) {
            rejectedOutcome(
                sequence,
                PlaybackCoordinatorFailureCode.ENGINE_FAILURE,
                failure.message ?: "Playback engine rejected the command"
            )
        }
        recordOutcome(sequence, intent, outcome)
    }

    private fun validate(intent: PlaybackIntent): String? = when (intent) {
        is PlaybackIntent.Invalid -> intent.diagnostic
        is PlaybackIntent.StartStandard,
        is PlaybackIntent.UpdateStandard -> {
            val standard = when (intent) {
                is PlaybackIntent.StartStandard -> intent
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
            if (ownership.value.activeMode == PlaybackMode.STANDARD) {
                null
            } else {
                "Standard update requires an active standard session"
            }
        is PlaybackIntent.UpdatePolyrhythm ->
            if (ownership.value.activeMode == PlaybackMode.POLYRHYTHM) {
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

    private fun applyStop(sequence: Long) {
        engine.stopMetronome()
        engine.stopPolyrhythm()
        mutateOwnership {
            it.copy(
                activeMode = PlaybackMode.NONE,
                lastCommandSequence = sequence
            )
        }
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
    private fun onSoundPreparation(
        active: ActiveSoundConfiguration?,
        failure: SoundPreparationFailure?
    ) {
        if (released) return
        executor.execute {
            val requested = ownership.value.requestedSounds
            val matches = active != null &&
                active.bank == requested.bank &&
                active.beatSound == requested.beatSound &&
                active.rhythmSound == requested.rhythmSound
            mutateOwnership {
                it.copy(
                    audibleSounds = if (matches) active else it.audibleSounds,
                    soundPreparationFailure = failure
                )
            }
            if (failure != null) {
                mutableControlEvents.tryEmit(
                    PlaybackControlEvent.SoundPreparationFailed(
                        ownership.value.lastCommandSequence,
                        failure
                    )
                )
            }
        }
    }

    private fun refreshAudibleSounds() {
        onSoundPreparation(
            engine.activeSoundConfiguration(),
            engine.soundPreparationFailure()
        )
    }

    private fun PlaybackIntent.immutableCopy(): PlaybackIntent = when (this) {
        is PlaybackIntent.StartStandard -> copy(accentPattern = accentPattern?.toList())
        is PlaybackIntent.UpdateStandard -> copy(accentPattern = accentPattern?.toList())
        is PlaybackIntent.PrepareSounds -> copy(sounds = sounds.toList())
        else -> this
    }

    private inline fun mutateOwnership(
        transform: (PlaybackOwnershipSnapshot) -> PlaybackOwnershipSnapshot
    ) {
        mutableOwnership.value = transform(mutableOwnership.value)
    }

    private companion object {
        const val TIMING_EVENT_CAPACITY = 64
        const val CONTROL_EVENT_CAPACITY = 64
    }
}
