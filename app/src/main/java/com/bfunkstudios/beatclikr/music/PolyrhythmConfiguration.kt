package com.bfunkstudios.beatclikr.music

data class PolyrhythmConfiguration(
    val bpm: ExactTempo,
    val beats: Int,
    val against: Int,
    val muteMetronome: Boolean = false
) {
    init {
        require(beats in SUPPORTED_COUNT) { "Beats must be between 1 and 15" }
        require(against in SUPPORTED_COUNT) { "Against must be between 1 and 15" }
    }

    val cycleDurationSeconds: ExactFraction
        get() = ExactFraction.of(against.toLong()) * bpm.quarterNoteDurationSeconds

    val beatCount: Int
        get() = against

    val rhythmCount: Int
        get() = beats

    val beatIntervalSeconds: ExactFraction
        get() = bpm.quarterNoteDurationSeconds

    val rhythmIntervalSeconds: ExactFraction
        get() = cycleDurationSeconds / ExactFraction.of(beats.toLong())

    companion object {
        val SUPPORTED_COUNT = 1..15
    }
}
