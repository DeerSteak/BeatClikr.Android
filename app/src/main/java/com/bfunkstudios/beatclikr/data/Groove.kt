package com.bfunkstudios.beatclikr.data

typealias Subdivisions = Int

enum class Groove(val subdivisions: Subdivisions) {
    Quarter(1),
    Eighth(2),
    Triplet(3),
    Sixteenth(4),
    OddMeterQuarter(1),
    OddMeterEighth(2);

    val isOddMeter: Boolean
        get() = this == OddMeterQuarter || this == OddMeterEighth

    companion object {
        val standardEntries = listOf(Quarter, Eighth, Triplet, Sixteenth)
        val selectableEntries = listOf(Quarter, Eighth, Triplet, Sixteenth, OddMeterQuarter, OddMeterEighth)
    }
}
