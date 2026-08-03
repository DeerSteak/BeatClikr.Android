package com.bfunkstudios.beatclikr.data.db

import androidx.room.TypeConverter
import com.bfunkstudios.beatclikr.data.BeatPattern
import com.bfunkstudios.beatclikr.data.Groove
import java.util.UUID

class Converters {
    @TypeConverter fun uuidToString(uuid: UUID): String = uuid.toString()
    @TypeConverter fun stringToUuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter fun grooveToString(value: Groove): String = "v1:${value.name}"
    @TypeConverter fun stringToGroove(value: String): Groove {
        val token = value.substringAfter("v1:", value)
        return GROOVE_ALIASES[token.lowercase()]
            ?: Groove.entries.firstOrNull { it.name.equals(token, ignoreCase = true) }
            ?: Groove.Quarter
    }

    @TypeConverter fun beatPatternToString(value: BeatPattern?): String? = value?.rawValue
    @TypeConverter fun stringToBeatPattern(value: String?): BeatPattern? =
        value?.let { BeatPattern.fromRawValue(it) }

    private companion object {
        val GROOVE_ALIASES = mapOf(
            "quarter_note" to Groove.Quarter,
            "eighth_note" to Groove.Eighth,
            "sixteenth_note" to Groove.Sixteenth
        )
    }
}
