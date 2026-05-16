package com.bfunkstudios.beatclikr.services

import android.content.Context
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile

/**
 * Centralized audio service for metronome playback.
 * Manages the audio engine and provides a clean API for playback control.
 */
class AudioPlayerService private constructor(context: Context) : IAudioPlayerService, MetronomeAudioEngineDelegate {
    private val audioEngine = MetronomeAudioEngine(context.applicationContext)

    override var delegate: MetronomeAudioEngineDelegate? = null
    override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate?
        get() = audioEngine.polyrhythmDelegate
        set(value) { audioEngine.polyrhythmDelegate = value }

    override var isMuted: Boolean
        get() = audioEngine.isMuted
        set(value) { audioEngine.isMuted = value }

    override var useAudioTrack: Boolean
        get() = audioEngine.useAudioTrack
        set(value) { audioEngine.useAudioTrack = value }

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
        audioEngine.stopPolyrhythm()
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
        audioEngine.stopMetronome()
        audioEngine.startPolyrhythm(bpm, beats, against)
    }

    override fun stopPolyrhythm() {
        audioEngine.stopPolyrhythm()
    }

    override fun prewarmAudioTrack() {
        audioEngine.prewarmAudioTrack()
    }

    override fun prepareAudioTrackSounds(soundFiles: Collection<SoundFile>) {
        audioEngine.prepareAudioTrackSounds(soundFiles)
    }

    override fun getAudioTrackMetricsSnapshot(): AudioTrackMetricsSnapshot? {
        return audioEngine.getAudioTrackMetricsSnapshot()
    }

    override fun release() {
        audioEngine.release()
        delegate = null
        synchronized(Companion) {
            INSTANCE = null
        }
    }

    override fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float, beatTimeNanos: Long) {
        delegate?.metronomeBeatFired(isBeat, beatInterval, beatTimeNanos)
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioPlayerService? = null

        fun getInstance(context: Context): AudioPlayerService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioPlayerService(context).also { INSTANCE = it }
            }
        }
    }
}
