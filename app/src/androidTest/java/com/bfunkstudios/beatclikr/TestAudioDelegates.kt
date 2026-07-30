package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import com.bfunkstudios.beatclikr.services.PolyrhythmAudioEngineDelegate

abstract class MetronomeTestDelegate : MetronomeAudioEngineDelegate {
    override fun metronomeStartFailed() = Unit
}

abstract class PolyrhythmTestDelegate : PolyrhythmAudioEngineDelegate {
    override fun polyrhythmStartFailed() = Unit
}
