package com.bfunkstudios.beatclikr.services

import android.content.Context

/**
 * Centralized audio service for metronome playback.
 * Manages the audio engine and provides a clean API for playback control.
 */
class AudioPlayerService private constructor(context: Context) : MetronomeAudioEngineDelegate {
    private val audioEngine = MetronomeAudioEngine(context.applicationContext)

    var delegate: MetronomeAudioEngineDelegate? = null

    fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int) {
        audioEngine.loadSounds(beatResourceId, rhythmResourceId)
    }

    fun startMetronome(bpm: Float, subdivisions: Int) {
        audioEngine.startMetronome(bpm, subdivisions, this)
    }

    fun stopMetronome() {
        audioEngine.stopMetronome()
    }

    fun updateTempo(bpm: Float, subdivisions: Int) {
        audioEngine.updateTempo(bpm, subdivisions)
    }

    fun release() {
        audioEngine.release()
        delegate = null
    }

    override fun metronomeBeatFired(isBeat: Boolean) {
        delegate?.metronomeBeatFired(isBeat)
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
