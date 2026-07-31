package com.bfunkstudios.beatclikr.services

import android.content.Context
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile

/** Centralized playback API backed by the metronome audio engine. */
internal class AudioPlayerService(context: Context) : PlaybackEnginePort, MetronomeAudioEngineDelegate {
    private val audioEngine = MetronomeAudioEngine(context.applicationContext)

    init {
        audioEngine.playbackInterruptionObserver = { sessionId, reason ->
            transportObserver?.engineInterrupted(sessionId, reason)
        }
    }

    override var soundPreparationObserver: ((SoundPreparationPublication) -> Unit)?
        get() = audioEngine.soundPreparationObserver
        set(value) {
            audioEngine.soundPreparationObserver = value
        }
    override var transportObserver: PlaybackEngineTransportObserver? = null

    override var delegate: MetronomeAudioEngineDelegate? = null
    override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate?
        get() = audioEngine.polyrhythmDelegate
        set(value) { audioEngine.polyrhythmDelegate = value }

    override var isMuted: Boolean
        get() = audioEngine.isMuted
        set(value) { audioEngine.isMuted = value }

    override fun selectSounds(
        requestSequence: Long,
        beatResourceId: Int,
        rhythmResourceId: Int
    ) {
        audioEngine.loadSounds(beatResourceId, rhythmResourceId, requestSequence)
    }

    override fun selectSoundBank(requestSequence: Long, bank: SoundBank) {
        audioEngine.selectSoundBank(bank, requestSequence)
    }

    override fun prepareSounds(requestSequence: Long, sounds: Collection<SoundFile>) {
        audioEngine.prepareAudioTrackSounds(sounds, requestSequence)
    }

    override fun prewarmAudioTrack() {
        audioEngine.prewarm()
    }

    override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? {
        return audioEngine.getFrameAudioMetricsSnapshot()
    }

    override fun activeSoundConfiguration(): ActiveSoundConfiguration? =
        audioEngine.getActiveSoundConfiguration()

    override fun soundPreparationFailure(): SoundPreparationFailure? =
        audioEngine.getSoundPreparationFailure()

    override fun drainRenderedEvents(
        afterCaptureSequence: Long
    ): FrameAudioRenderedEventBatch? =
        audioEngine.drainRenderedEvents(afterCaptureSequence)

    override fun beginStandardSession(
        sessionId: PlaybackSessionId,
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {
        audioEngine.startMetronome(
            bpm,
            subdivisions,
            accentPattern,
            alternateSixteenth,
            this,
            sessionId,
            ::publishStartResult
        )
    }

    override fun beginPolyrhythmSession(
        sessionId: PlaybackSessionId,
        bpm: Float,
        beats: Int,
        against: Int
    ) {
        audioEngine.startPolyrhythm(sessionId, bpm, beats, against, ::publishStartResult)
    }

    override fun updateStandardSession(
        sessionId: PlaybackSessionId,
        configuration: CommittedPlaybackConfiguration.Standard,
        completion: (PlaybackEngineUpdateResult) -> Unit
    ) {
        audioEngine.updateStandardSession(sessionId, configuration, completion)
    }

    override fun updatePolyrhythmSession(
        sessionId: PlaybackSessionId,
        configuration: CommittedPlaybackConfiguration.Polyrhythm,
        completion: (PlaybackEngineUpdateResult) -> Unit
    ) {
        audioEngine.updatePolyrhythmSession(sessionId, configuration, completion)
    }

    override fun stopSession(sessionId: PlaybackSessionId, mode: PlaybackMode) {
        audioEngine.stopSession(mode) {
            transportObserver?.engineStopped(sessionId)
        }
    }

    override fun release() {
        audioEngine.playbackInterruptionObserver = null
        audioEngine.release()
        delegate = null
        transportObserver = null
    }

    override fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float, beatTimeNanos: Long) {
        delegate?.metronomeBeatFired(isBeat, beatInterval, beatTimeNanos)
    }

    override fun metronomeStartFailed() {
        delegate?.metronomeStartFailed()
    }

    private fun publishStartResult(
        sessionId: PlaybackSessionId,
        result: AudioEngineStartResult
    ) {
        if (result is AudioEngineStartResult.AudioFocusUnavailable) {
            transportObserver?.audioFocusUnavailable(sessionId)
            return
        }
        if (result is AudioEngineStartResult.StreamFailed) {
            transportObserver?.engineStartFailed(sessionId, "Audio stream failed to start")
            return
        }
        val evidence = (result as AudioEngineStartResult.Started).evidence
        val sounds = activeSoundConfiguration()
        if (sounds == null) {
            transportObserver?.engineStartFailed(
                sessionId,
                "Audio stream started without prepared sounds"
            )
            return
        }
        transportObserver?.engineStarted(
            PlaybackEngineStartEvidence(
                sessionId,
                sounds,
                evidence.route,
                evidence.backend,
                evidence.firstEventFrame
            )
        )
    }
}
