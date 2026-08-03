package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile

interface PlaybackEnginePort {
    var soundPreparationObserver: ((SoundPreparationPublication) -> Unit)?
    var transportObserver: PlaybackEngineTransportObserver?
    var isMuted: Boolean

    fun activeSoundConfiguration(): ActiveSoundConfiguration?
    fun soundPreparationFailure(): SoundPreparationFailure?
    fun prewarmAudioTrack()
    fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot?
    fun release()
    fun beginStandardSession(sessionId: PlaybackSessionId, configuration: ValidatedStandardConfiguration)
    fun beginPolyrhythmSession(sessionId: PlaybackSessionId, configuration: ValidatedPolyrhythmConfiguration)
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
    fun selectSounds(requestSequence: Long, beatResourceId: Int, rhythmResourceId: Int)
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
    fun engineInterrupted(sessionId: PlaybackSessionId, reason: PlaybackInterruptionReason)
}
