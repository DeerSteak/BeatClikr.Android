package com.bfunkstudios.beatclikr.data

data class PolyrhythmStep(
    val beatFired: Boolean,
    val rhythmFired: Boolean,
    val beatIndex: Int,
    val rhythmIndex: Int
)

data class PolyrhythmGrid(
    val beats: Int,
    val against: Int
) {
    val lcm: Int = computeLCM(beats, against)
    val beatGridStep: Int = lcm / against
    val rhythmGridStep: Int = lcm / beats

    fun stepAt(stepIndex: Int): PolyrhythmStep {
        val normalizedIndex = ((stepIndex % lcm) + lcm) % lcm
        val beatFired = normalizedIndex % beatGridStep == 0
        val rhythmFired = normalizedIndex % rhythmGridStep == 0
        return PolyrhythmStep(
            beatFired = beatFired,
            rhythmFired = rhythmFired,
            beatIndex = normalizedIndex / beatGridStep,
            rhythmIndex = normalizedIndex / rhythmGridStep
        )
    }

    companion object {
        fun create(beats: Int, against: Int): PolyrhythmGrid =
            PolyrhythmGrid(
                beats = beats.coerceIn(1, 15),
                against = against.coerceIn(1, 15)
            )

        private fun computeLCM(a: Int, b: Int): Int = a / computeGCD(a, b) * b

        private fun computeGCD(aValue: Int, bValue: Int): Int {
            var a = aValue
            var b = bValue
            while (b != 0) {
                val next = a % b
                a = b
                b = next
            }
            return a
        }
    }
}
