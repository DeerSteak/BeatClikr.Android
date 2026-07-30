package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    val lastCommandSequence: Long = 0
)

sealed interface PlaybackIntent {
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

sealed interface PlaybackCoordinatorEvent {
    data class StandardTiming(
        val isBeat: Boolean,
        val beatInterval: Float,
        val beatTimeNanos: Long
    ) : PlaybackCoordinatorEvent

    data class PolyrhythmTiming(
        val beatFired: Boolean,
        val rhythmFired: Boolean,
        val beatIndex: Int,
        val rhythmIndex: Int,
        val stepTimeNanos: Long,
        val beatDurationNanos: Long,
        val rhythmDurationNanos: Long
    ) : PlaybackCoordinatorEvent

    data class IntentAccepted(
        val commandSequence: Long,
        val intent: PlaybackIntent
    ) : PlaybackCoordinatorEvent

    data class IntentRejected(
        val commandSequence: Long,
        val failure: PlaybackCoordinatorFailure
    ) : PlaybackCoordinatorEvent

    data class SoundPreparationFailed(
        val commandSequence: Long,
        val failure: SoundPreparationFailure
    ) : PlaybackCoordinatorEvent
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
    private val mutableEvents = MutableSharedFlow<PlaybackCoordinatorEvent>(
        extraBufferCapacity = EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var nextCommandSequence = 1L
    @Volatile
    private var released = false

    val ownership: StateFlow<PlaybackOwnershipSnapshot> = mutableOwnership
    val events: SharedFlow<PlaybackCoordinatorEvent> = mutableEvents

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

    fun submit(intent: PlaybackIntent): PlaybackIntentOutcome =
        executor.submit<PlaybackIntentOutcome> { applyIntent(intent) }.get()

    override fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int) {
        val beat = SoundFile.fromResourceId(beatResourceId)
        val rhythm = SoundFile.fromResourceId(rhythmResourceId)
        if (beat == null || rhythm == null) {
            rejectInvalid("Unknown sound resource")
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
        submit(
            if (ownership.value.activeMode == PlaybackMode.POLYRHYTHM) {
                PlaybackIntent.UpdatePolyrhythm(bpm, beats, against)
            } else {
                PlaybackIntent.StartPolyrhythm(bpm, beats, against)
            }
        )
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

    override fun release() {
        if (released) return
        executor.submit {
            applyIntent(PlaybackIntent.Stop)
            released = true
            engine.soundPreparationObserver = null
            engine.delegate = null
            engine.polyrhythmDelegate = null
            engine.release()
        }.get()
        executor.shutdown()
        delegate = null
        polyrhythmDelegate = null
    }

    override fun metronomeBeatFired(
        isBeat: Boolean,
        beatInterval: Float,
        beatTimeNanos: Long
    ) {
        mutableEvents.tryEmit(
            PlaybackCoordinatorEvent.StandardTiming(isBeat, beatInterval, beatTimeNanos)
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
        mutableEvents.tryEmit(
            PlaybackCoordinatorEvent.PolyrhythmTiming(
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

    private fun applyIntent(intent: PlaybackIntent): PlaybackIntentOutcome {
        val sequence = nextCommandSequence++
        if (released) return rejected(
            sequence,
            PlaybackCoordinatorFailureCode.RELEASED,
            "Playback coordinator is released"
        )
        val validationFailure = validate(intent)
        if (validationFailure != null) {
            return rejected(
                sequence,
                PlaybackCoordinatorFailureCode.INVALID_INPUT,
                validationFailure
            )
        }
        return try {
            when (intent) {
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
                    engine.stopPolyrhythm()
                    engine.startMetronome(
                        intent.bpm,
                        intent.subdivisions,
                        intent.accentPattern,
                        intent.alternateSixteenth
                    )
                    mutateOwnership { it.copy(activeMode = PlaybackMode.STANDARD) }
                }
                is PlaybackIntent.UpdateStandard -> engine.updateTempo(
                    intent.bpm,
                    intent.subdivisions,
                    intent.accentPattern,
                    intent.alternateSixteenth
                )
                is PlaybackIntent.StartPolyrhythm -> {
                    engine.stopMetronome()
                    engine.startPolyrhythm(intent.bpm, intent.beats, intent.against)
                    mutateOwnership { it.copy(activeMode = PlaybackMode.POLYRHYTHM) }
                }
                is PlaybackIntent.UpdatePolyrhythm ->
                    engine.startPolyrhythm(intent.bpm, intent.beats, intent.against)
                PlaybackIntent.Stop -> {
                    engine.stopMetronome()
                    engine.stopPolyrhythm()
                    mutateOwnership { it.copy(activeMode = PlaybackMode.NONE) }
                }
                PlaybackIntent.Prewarm -> engine.prewarmAudioTrack()
                is PlaybackIntent.PrepareSounds ->
                    engine.prepareAudioTrackSounds(intent.sounds)
            }
            mutateOwnership { it.copy(lastCommandSequence = sequence) }
            mutableEvents.tryEmit(PlaybackCoordinatorEvent.IntentAccepted(sequence, intent))
            PlaybackIntentOutcome.Accepted(sequence)
        } catch (failure: RuntimeException) {
            rejected(
                sequence,
                PlaybackCoordinatorFailureCode.ENGINE_FAILURE,
                failure.message ?: "Playback engine rejected the command"
            )
        }
    }

    private fun validate(intent: PlaybackIntent): String? = when (intent) {
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

    private fun rejectInvalid(diagnostic: String): PlaybackIntentOutcome =
        executor.submit<PlaybackIntentOutcome> {
            val sequence = nextCommandSequence++
            rejected(
                sequence,
                PlaybackCoordinatorFailureCode.INVALID_INPUT,
                diagnostic
            )
        }.get()

    private fun rejected(
        sequence: Long,
        code: PlaybackCoordinatorFailureCode,
        diagnostic: String
    ): PlaybackIntentOutcome.Rejected {
        val failure = PlaybackCoordinatorFailure(code, diagnostic)
        mutableEvents.tryEmit(PlaybackCoordinatorEvent.IntentRejected(sequence, failure))
        mutateOwnership { it.copy(lastCommandSequence = sequence) }
        return PlaybackIntentOutcome.Rejected(sequence, failure)
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
                mutableEvents.tryEmit(
                    PlaybackCoordinatorEvent.SoundPreparationFailed(
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

    private inline fun mutateOwnership(
        transform: (PlaybackOwnershipSnapshot) -> PlaybackOwnershipSnapshot
    ) {
        mutableOwnership.value = transform(mutableOwnership.value)
    }

    private companion object {
        const val EVENT_CAPACITY = 64
    }
}
