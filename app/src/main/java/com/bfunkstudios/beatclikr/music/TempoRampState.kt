package com.bfunkstudios.beatclikr.music

data class TempoRampConfiguration(
    val rampIncrement: Int,
    val rampInterval: Int
) {
    init {
        require(rampIncrement in supportedIncrements) { "Unsupported ramp increment" }
        require(rampInterval in supportedIntervals) { "Unsupported ramp interval" }
    }

    companion object {
        val supportedIncrements = listOf(1, 2, 5, 10)
        val supportedIntervals = listOf(4, 8, 16, 32, 48, 64)
    }
}

data class TempoRampState(
    val startingBpm: ExactTempo,
    val currentBpm: ExactTempo = startingBpm,
    val rampBeatCount: Long = -1
) {
    init {
        require(rampBeatCount >= -1) { "Ramp beat count must not be less than -1" }
    }

    fun advance(
        configuration: TempoRampConfiguration,
        event: FrameEvent
    ): TempoRampAdvance {
        if (!event.isRampBeat()) return TempoRampAdvance(this)
        val nextCount = Math.incrementExact(rampBeatCount)
        val shouldStep = nextCount > 0 && nextCount % configuration.rampInterval == 0L
        if (!shouldStep) return TempoRampAdvance(copy(rampBeatCount = nextCount))
        val nextBpm = currentBpm.increasedBy(configuration.rampIncrement)
        val nextState = copy(currentBpm = nextBpm, rampBeatCount = nextCount)
        return TempoRampAdvance(
            state = nextState,
            restartBpm = nextBpm.takeIf { it != currentBpm }
        )
    }
}

data class TempoRampAdvance(
    val state: TempoRampState,
    val restartBpm: ExactTempo? = null
)

private fun FrameEvent.isRampBeat(): Boolean =
    primary.role == MusicalEventRole.STANDARD &&
        primary.beatIdentity != BeatIdentity.SUBDIVISION
