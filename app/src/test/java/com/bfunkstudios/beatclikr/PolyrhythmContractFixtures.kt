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
            stepIndex = stepIndex,
            beatFired = beatFired,
            rhythmFired = rhythmFired,
            beatIndex = stepIndex / beatStep,
            rhythmIndex = stepIndex / rhythmStep
        )
    }

    private fun leastCommonMultiple(first: Int, second: Int): Int = first / greatestCommonDivisor(first, second) * second

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

    val representativeRatios = listOf(
        PolyrhythmContractFixture(1, 1),
        PolyrhythmContractFixture(3, 2),
        PolyrhythmContractFixture(4, 3),
        PolyrhythmContractFixture(6, 4),
        PolyrhythmContractFixture(15, 1),
        PolyrhythmContractFixture(1, 15)
    )
}
