package com.bfunkstudios.beatclikr.data.db

import androidx.room.TypeConverter
import com.bfunkstudios.beatclikr.data.Subdivisions
import java.util.UUID

class Converters {
    @TypeConverter fun uuidToString(uuid: UUID): String = uuid.toString()
    @TypeConverter fun stringToUuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter fun subdivisionsToString(value: Subdivisions): String = value.name
    @TypeConverter fun stringToSubdivisions(value: String): Subdivisions = Subdivisions.valueOf(value)
}
