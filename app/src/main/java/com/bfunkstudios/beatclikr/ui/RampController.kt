package com.bfunkstudios.beatclikr.ui

import com.bfunkstudios.beatclikr.constants.MetronomeConstants

class RampController(
    var enabled: Boolean = false,
    var increment: Int = 1,
    var interval: Int = 4
) {
    private var beatCount = -1

    fun reset() {
        beatCount = -1
    }

    fun onBeat(currentBpm: Float): Float? {
        if (!enabled) return null
        beatCount++
        if (beatCount <= 0 || beatCount % interval != 0) return null
        val newBpm = (currentBpm + increment).coerceAtMost(MetronomeConstants.MAX_BPM)
        return if (newBpm != currentBpm) newBpm else null
    }
}
