package com.bfunkstudios.beatclikr.data

import androidx.room.ColumnInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PracticeDayIdentity(
    @ColumnInfo(name = "local_day_key") val localDayKey: String,
    @ColumnInfo(name = "time_zone_identifier") val timeZoneIdentifier: String,
    @ColumnInfo(name = "utc_offset_seconds") val utcOffsetSeconds: Int,
    @ColumnInfo(name = "calendar_identifier") val calendarIdentifier: String,
    @ColumnInfo(name = "original_timestamp_millis") val originalTimestampMillis: Long
) {
    companion object {
        const val GREGORIAN_CALENDAR = "gregorian"

        fun capture(
            instant: Instant = Instant.now(),
            timeZone: ZoneId = ZoneId.systemDefault()
        ): PracticeDayIdentity {
            val zoned = instant.atZone(timeZone)
            return PracticeDayIdentity(
                localDayKey = zoned.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                timeZoneIdentifier = timeZone.id,
                utcOffsetSeconds = zoned.offset.totalSeconds,
                calendarIdentifier = GREGORIAN_CALENDAR,
                originalTimestampMillis = instant.toEpochMilli()
            )
        }

        fun startOfDayMillis(localDayKey: String, timeZoneIdentifier: String): Long =
            LocalDate.parse(localDayKey, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneId.of(timeZoneIdentifier))
                .toInstant()
                .toEpochMilli()
    }
}
