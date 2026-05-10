package com.bfunkstudios.beatclikr.services

interface IAudioPlayerService {
    var delegate: MetronomeAudioEngineDelegate?
    var isMuted: Boolean
    fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int)
    fun startMetronome(bpm: Float, subdivisions: Int, accentPattern: List<Boolean>? = null)
    fun stopMetronome()
    fun updateTempo(bpm: Float, subdivisions: Int, accentPattern: List<Boolean>? = null)
    fun release()
}
