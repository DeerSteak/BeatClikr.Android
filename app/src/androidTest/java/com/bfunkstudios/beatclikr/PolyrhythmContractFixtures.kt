package com.bfunkstudios.beatclikr

internal data class EnginePolyrhythmEvent(
    val stepIndex: Int,
    val beatFired: Boolean,
    val rhythmFired: Boolean,
    val beatIndex: Int,
    val rhythmIndex: Int
)

internal data class EnginePolyrhythmFixture(
    val beats: Int,
    val against: Int
) {
    val gridSize = leastCommonMultiple(beats, against)
    val events = (0 until gridSize).mapNotNull(::eventAt)

    private fun eventAt(stepIndex: Int): EnginePolyrhythmEvent? {
        val beatStep = gridSize / against
        val rhythmStep = gridSize / beats
        val beatFired = stepIndex % beatStep == 0
        val rhythmFired = stepIndex % rhythmStep == 0
        if (!beatFired && !rhythmFired) return null
        return EnginePolyrhythmEvent(
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

internal object EnginePolyrhythmFixtures {
    val representativeRatios = listOf(
        EnginePolyrhythmFixture(1, 1),
        EnginePolyrhythmFixture(3, 2),
        EnginePolyrhythmFixture(4, 3),
        EnginePolyrhythmFixture(6, 4),
        EnginePolyrhythmFixture(15, 1),
        EnginePolyrhythmFixture(1, 15)
    )
}
