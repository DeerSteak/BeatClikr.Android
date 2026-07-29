package com.bfunkstudios.beatclikr.music

enum class StandardSubdivision(val subdivisions: Int) {
    QUARTER(1),
    EIGHTH(2),
    TRIPLET(3),
    SIXTEENTH(4)
}

enum class AdditiveStepUnit(val subdivisions: Int) {
    QUARTER(1),
    EIGHTH(2)
}

class AccentPattern private constructor(
    private val accents: List<Boolean>
) {
    val size: Int
        get() = accents.size

    operator fun get(index: Int): Boolean = accents[index]

    fun toList(): List<Boolean> = accents.toList()

    override fun equals(other: Any?): Boolean =
        other is AccentPattern && accents == other.accents

    override fun hashCode(): Int = accents.hashCode()

    override fun toString(): String = accents.toString()

    companion object {
        fun of(values: Iterable<Boolean>): AccentPattern {
            val copy = values.toList()
            require(copy.isNotEmpty()) { "Accent pattern must not be empty" }
            require(copy.first()) { "Accent pattern must begin with an accent" }
            return AccentPattern(copy)
        }
    }
}

sealed interface StandardTiming {
    data class Regular(val subdivision: StandardSubdivision) : StandardTiming

    data class Additive(
        val stepUnit: AdditiveStepUnit,
        val accents: AccentPattern
    ) : StandardTiming
}

data class StandardMetronomeConfiguration(
    val tempo: ExactTempo,
    val timing: StandardTiming,
    val alternateSixteenth: Boolean = false,
    val muteMetronome: Boolean = false
)
