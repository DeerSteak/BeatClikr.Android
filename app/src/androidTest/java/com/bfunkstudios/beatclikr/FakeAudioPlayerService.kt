package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate

class FakeAudioPlayerService : IAudioPlayerService {
    override var delegate: MetronomeAudioEngineDelegate? = null
    override var isMuted: Boolean = false

    var startCount = 0
    var stopCount = 0

    override fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int) {}
    override fun startMetronome(bpm: Float, subdivisions: Int, accentPattern: List<Boolean>?) { startCount++ }
    override fun stopMetronome() { stopCount++ }
    override fun updateTempo(bpm: Float, subdivisions: Int, accentPattern: List<Boolean>?) {}
    override fun release() {}
}
