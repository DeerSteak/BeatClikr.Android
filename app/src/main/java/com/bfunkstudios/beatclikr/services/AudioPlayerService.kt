package com.bfunkstudios.beatclikr.services

import android.content.Context

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

    override fun release() {
        audioEngine.release()
        delegate = null
        synchronized(AudioPlayerService::class.java) {
            INSTANCE = null
        }
    }

    override fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float) {
        delegate?.metronomeBeatFired(isBeat, beatInterval)
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
