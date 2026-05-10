package com.bfunkstudios.beatclikr.data

enum class BeatPattern(val rawValue: String, val displayName: String) {
    FiveEightA("3,2", "5 (3+2)"),
    FiveEightB("2,3", "5 (2+3)"),
    SevenEightA("3,2,2", "7 (3+2+2)"),
    SevenEightB("2,2,3", "7 (2+2+3)"),
    SevenEightC("2,3,2", "7 (2+3+2)"),
    NineEightA("2,2,2,3", "9 (2+2+2+3)"),
    NineEightB("3,3,3", "9 (3+3+3)"),
    ElevenEightA("2,2,3,2,2", "11 (2+2+3+2+2)"),
    ElevenEightB("3,3,2,3", "11 (3+3+2+3)"),
    ThirteenEightA("3,2,2,3,3", "13 (3+2+2+3+3)"),
    ThirteenEightB("2,3,2,3,3", "13 (2+3+2+3+3)"),
    FifteenEightA("3,3,3,3,3", "15 (3+3+3+3+3)"),
    FifteenEightB("2,3,2,3,2,3", "15 (2+3+2+3+2+3)");

    val accentArray: List<Boolean>
        get() = rawValue
            .split(",")
            .mapNotNull { it.toIntOrNull() }
            .flatMap { count -> listOf(true) + List(count - 1) { false } }

    companion object {
        val default = SevenEightA

        fun fromRawValue(rawValue: String): BeatPattern? =
            entries.firstOrNull { it.rawValue == rawValue }
    }
}
