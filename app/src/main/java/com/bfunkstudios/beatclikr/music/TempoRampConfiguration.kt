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
