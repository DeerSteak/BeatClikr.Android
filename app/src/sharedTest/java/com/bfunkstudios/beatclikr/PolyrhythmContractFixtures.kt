package com.bfunkstudios.beatclikr

internal data class PolyrhythmContractEvent(
    val stepIndex: Int,
    val beatFired: Boolean,
    val rhythmFired: Boolean,
    val beatIndex: Int,
    val rhythmIndex: Int
)

internal data class PolyrhythmContractFixture(
    val beats: Int,
    val against: Int
) {
    val gridSize = leastCommonMultiple(beats, against)
    val events = (0 until gridSize).mapNotNull(::eventAt)

    fun cycleDurationNanos(bpm: Double): Double = against * 60_000_000_000.0 / bpm

    private fun eventAt(stepIndex: Int): PolyrhythmContractEvent? {
        val beatStep = gridSize / against
        val rhythmStep = gridSize / beats
        val beatFired = stepIndex % beatStep == 0
        val rhythmFired = stepIndex % rhythmStep == 0
        if (!beatFired && !rhythmFired) return null
        return PolyrhythmContractEvent(
            stepIndex,
            beatFired,
            rhythmFired,
            stepIndex / beatStep,
            stepIndex / rhythmStep
        )
    }

    private fun leastCommonMultiple(first: Int, second: Int): Int =
        first / greatestCommonDivisor(first, second) * second

    private fun greatestCommonDivisor(first: Int, second: Int): Int {
        var left = first
        var right = second
        while (right != 0) {
            val remainder = left % right
            left = right
            right = remainder
        }
        return left
    }
}

internal object PolyrhythmContractFixtures {
    val allRatios = (1..15).flatMap { beats ->
        (1..15).map { against -> PolyrhythmContractFixture(beats, against) }
    }

    val representativeRatios = listOf(1 to 1, 3 to 2, 4 to 3, 6 to 4, 15 to 1, 1 to 15)
        .map { (beats, against) -> PolyrhythmContractFixture(beats, against) }
}

internal typealias EnginePolyrhythmEvent = PolyrhythmContractEvent
internal typealias EnginePolyrhythmFixture = PolyrhythmContractFixture

internal object EnginePolyrhythmFixtures {
    val representativeRatios = PolyrhythmContractFixtures.representativeRatios
}
