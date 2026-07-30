package com.bfunkstudios.beatclikr.services

import android.content.Context
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile

/** Centralized playback API backed by the metronome audio engine. */
internal class AudioPlayerService(context: Context) : PlaybackEnginePort, MetronomeAudioEngineDelegate {
    private val audioEngine = MetronomeAudioEngine(context.applicationContext)

    override var soundPreparationObserver:
        ((ActiveSoundConfiguration?, SoundPreparationFailure?) -> Unit)?
        get() = audioEngine.soundPreparationObserver
        set(value) {
            audioEngine.soundPreparationObserver = value
        }

    override var delegate: MetronomeAudioEngineDelegate? = null
    override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate?
        get() = audioEngine.polyrhythmDelegate
        set(value) { audioEngine.polyrhythmDelegate = value }

    override var isMuted: Boolean
        get() = audioEngine.isMuted
        set(value) { audioEngine.isMuted = value }

    override var soundBank: SoundBank
        get() = audioEngine.soundBank
        set(value) { audioEngine.soundBank = value }

    override fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int) {
        audioEngine.loadSounds(beatResourceId, rhythmResourceId)
    }

    override fun startMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {
        audioEngine.startMetronome(bpm, subdivisions, accentPattern, alternateSixteenth, this)
    }

    override fun stopMetronome() {
        audioEngine.stopMetronome()
    }

    override fun updateTempo(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {
        audioEngine.updateTempo(bpm, subdivisions, accentPattern, alternateSixteenth)
    }

    override fun startPolyrhythm(bpm: Float, beats: Int, against: Int) {
        audioEngine.startPolyrhythm(bpm, beats, against)
    }

    override fun stopPolyrhythm() {
        audioEngine.stopPolyrhythm()
    }

    override fun prewarmAudioTrack() {
        audioEngine.prewarm()
    }

    override fun prepareAudioTrackSounds(soundFiles: Collection<SoundFile>) {
        audioEngine.prepareAudioTrackSounds(soundFiles)
    }

    override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? {
        return audioEngine.getFrameAudioMetricsSnapshot()
    }

    override fun activeSoundConfiguration(): ActiveSoundConfiguration? =
        audioEngine.getActiveSoundConfiguration()

    override fun soundPreparationFailure(): SoundPreparationFailure? =
        audioEngine.getSoundPreparationFailure()

    override fun release() {
        audioEngine.release()
        delegate = null
    }

    override fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float, beatTimeNanos: Long) {
        delegate?.metronomeBeatFired(isBeat, beatInterval, beatTimeNanos)
    }

    override fun metronomeStartFailed() {
        delegate?.metronomeStartFailed()
    }

}
