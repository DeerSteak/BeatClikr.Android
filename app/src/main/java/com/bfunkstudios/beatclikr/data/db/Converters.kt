package com.bfunkstudios.beatclikr.data.db

import androidx.room.TypeConverter
import com.bfunkstudios.beatclikr.data.BeatPattern
import com.bfunkstudios.beatclikr.data.Groove
import java.util.UUID

class Converters {
    @TypeConverter fun uuidToString(uuid: UUID): String = uuid.toString()
    @TypeConverter fun stringToUuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter fun grooveToString(value: Groove): String = value.name
    @TypeConverter fun stringToGroove(value: String): Groove = Groove.valueOf(value)

    @TypeConverter fun beatPatternToString(value: BeatPattern?): String? = value?.rawValue
    @TypeConverter fun stringToBeatPattern(value: String?): BeatPattern? =
        value?.let { BeatPattern.fromRawValue(it) }
}
