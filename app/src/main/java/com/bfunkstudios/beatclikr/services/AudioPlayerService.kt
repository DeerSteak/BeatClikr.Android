package com.bfunkstudios.beatclikr.services

import android.content.Context

/**
 * Centralized audio service for metronome playback.
 * Manages the audio engine and provides a clean API for playback control.
 */
class AudioPlayerService private constructor(context: Context) : IAudioPlayerService, MetronomeAudioEngineDelegate {
    private val audioEngine = MetronomeAudioEngine(context.applicationContext)

    override var delegate: MetronomeAudioEngineDelegate? = null
    override var isMuted: Boolean
        get() = audioEngine.isMuted
        set(value) { audioEngine.isMuted = value }

    override fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int) {
        audioEngine.loadSounds(beatResourceId, rhythmResourceId)
    }

    override fun startMetronome(bpm: Float, subdivisions: Int, accentPattern: List<Boolean>?) {
        audioEngine.startMetronome(bpm, subdivisions, accentPattern, this)
    }

    override fun stopMetronome() {
        audioEngine.stopMetronome()
    }

    override fun updateTempo(bpm: Float, subdivisions: Int, accentPattern: List<Boolean>?) {
        audioEngine.updateTempo(bpm, subdivisions, accentPattern)
    }

    override fun release() {
        audioEngine.release()
        delegate = null
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
