package com.bfunkstudios.beatclikr.music

import java.math.BigInteger

class ExactFraction private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger
) : Comparable<ExactFraction> {

    init {
        require(denominator > BigInteger.ZERO) { "Denominator must be positive" }
    }

    operator fun times(other: ExactFraction): ExactFraction =
        create(numerator * other.numerator, denominator * other.denominator)

    operator fun times(other: Long): ExactFraction =
        create(numerator * BigInteger.valueOf(other), denominator)

    operator fun plus(other: ExactFraction): ExactFraction =
        create(
            numerator * other.denominator + other.numerator * denominator,
            denominator * other.denominator
        )

    operator fun div(other: ExactFraction): ExactFraction {
        require(other.numerator != BigInteger.ZERO) { "Cannot divide by zero" }
        return create(numerator * other.denominator, denominator * other.numerator)
    }

    override fun compareTo(other: ExactFraction): Int =
        (numerator * other.denominator).compareTo(other.numerator * denominator)

    override fun equals(other: Any?): Boolean =
        other is ExactFraction && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String =
        if (denominator == BigInteger.ONE) numerator.toString() else "$numerator/$denominator"

    fun roundedLong(): Long {
        val division = numerator.divideAndRemainder(denominator)
        val quotient = division[0]
        val remainder = division[1]
        val adjustment = when {
            remainder.abs() * BigInteger.valueOf(2) < denominator -> BigInteger.ZERO
            numerator.signum() >= 0 -> BigInteger.ONE
            else -> BigInteger.ONE.negate()
        }
        return (quotient + adjustment).longValueExact()
    }

    companion object {
        fun of(value: Long): ExactFraction = create(BigInteger.valueOf(value), BigInteger.ONE)

        fun parseDecimal(value: String): ExactFraction {
            val decimal = value.trim().toBigDecimal()
            val unscaled = decimal.unscaledValue()
            val scale = decimal.scale()
            return if (scale >= 0) {
                create(unscaled, BigInteger.TEN.pow(scale))
            } else {
                create(unscaled * BigInteger.TEN.pow(-scale), BigInteger.ONE)
            }
        }

        private fun create(numerator: BigInteger, denominator: BigInteger): ExactFraction {
            require(denominator != BigInteger.ZERO) { "Denominator must not be zero" }
            if (numerator == BigInteger.ZERO) return ExactFraction(BigInteger.ZERO, BigInteger.ONE)
            val sign = if (denominator.signum() < 0) BigInteger.valueOf(-1) else BigInteger.ONE
            val signedNumerator = numerator * sign
            val positiveDenominator = denominator * sign
            val divisor = signedNumerator.abs().gcd(positiveDenominator)
            return ExactFraction(signedNumerator / divisor, positiveDenominator / divisor)
        }
    }
}

class ExactTempo private constructor(
    val beatsPerMinute: ExactFraction
) {
    val quarterNoteDurationSeconds: ExactFraction
        get() = ExactFraction.of(60) / beatsPerMinute

    fun framesPerQuarter(sampleRate: Int): ExactFraction {
        require(sampleRate > 0) { "Sample rate must be positive" }
        return ExactFraction.of(sampleRate.toLong()) * quarterNoteDurationSeconds
    }

    fun increasedBy(increment: Int): ExactTempo {
        require(increment > 0) { "Tempo increment must be positive" }
        val increased = beatsPerMinute + ExactFraction.of(increment.toLong())
        return ExactTempo(if (increased > maximumBpm) maximumBpm else increased)
    }

    override fun equals(other: Any?): Boolean =
        other is ExactTempo && beatsPerMinute == other.beatsPerMinute

    override fun hashCode(): Int = beatsPerMinute.hashCode()

    override fun toString(): String = beatsPerMinute.toString()

    companion object {
        private val minimumBpm = ExactFraction.of(30)
        private val maximumBpm = ExactFraction.of(240)

        val minimum = ExactTempo(minimumBpm)
        val maximum = ExactTempo(maximumBpm)

        fun of(value: Int): ExactTempo = fromFraction(ExactFraction.of(value.toLong()))

        fun parse(value: String): ExactTempo = fromFraction(ExactFraction.parseDecimal(value))

        fun fromFloat(value: Float): ExactTempo = parse(value.toString())

        private fun fromFraction(value: ExactFraction): ExactTempo {
            require(value >= minimumBpm && value <= maximumBpm) {
                "Tempo must be between 30 and 240 BPM"
            }
            return ExactTempo(value)
        }
    }
}
