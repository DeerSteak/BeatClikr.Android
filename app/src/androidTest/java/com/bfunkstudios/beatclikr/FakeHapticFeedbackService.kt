package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.IHapticFeedbackService

class FakeHapticFeedbackService : IHapticFeedbackService {
    var beatCount = 0
    var rhythmCount = 0

    override fun playBeatHaptic() {
        beatCount += 1
    }

    override fun playRhythmHaptic() {
        rhythmCount += 1
    }
}
