package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import com.bfunkstudios.beatclikr.services.PolyrhythmAudioEngineDelegate

class FakeAudioPlayerService : IAudioPlayerService {
    override var delegate: MetronomeAudioEngineDelegate? = null
    override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate? = null
    override var isMuted: Boolean = false

    var startCount = 0
    var stopCount = 0

    override fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int) {}
    override fun startMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) { startCount++ }
    override fun stopMetronome() { stopCount++ }
    override fun updateTempo(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {}
    override fun startPolyrhythm(bpm: Float, beats: Int, against: Int) {}
    override fun stopPolyrhythm() {}
    override fun release() {}
}
