package com.bfunkstudios.beatclikr.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PracticeDayIdentityTest {
    @Test
    fun captureStoresCivilDayMetadataSeparatelyFromOriginalInstant() {
        val instant = Instant.parse("2026-03-09T04:30:00Z")

        val identity = PracticeDayIdentity.capture(instant, ZoneId.of("America/Chicago"))

        assertEquals("2026-03-08", identity.localDayKey)
        assertEquals("America/Chicago", identity.timeZoneIdentifier)
        assertEquals(-18_000, identity.utcOffsetSeconds)
        assertEquals("gregorian", identity.calendarIdentifier)
        assertEquals(instant.toEpochMilli(), identity.originalTimestampMillis)
    }

    @Test
    fun sameInstantUsesTheCheckpointTimeZoneWithoutRelabelingPriorIdentity() {
        val instant = Instant.parse("2026-03-09T04:30:00Z")
        val chicago = PracticeDayIdentity.capture(instant, ZoneId.of("America/Chicago"))
        val tokyo = PracticeDayIdentity.capture(instant, ZoneId.of("Asia/Tokyo"))

        assertEquals("2026-03-08", chicago.localDayKey)
        assertEquals("2026-03-09", tokyo.localDayKey)
        assertNotEquals(chicago.localDayKey, tokyo.localDayKey)
    }

    @Test
    fun daylightSavingGapUsesTheRealGregorianDayStart() {
        val start = PracticeDayIdentity.startOfDayMillis(
            "2026-03-08",
            "America/Chicago"
        )

        assertEquals(Instant.parse("2026-03-08T06:00:00Z").toEpochMilli(), start)
    }

    @Test
    fun daylightSavingOverlapKeepsOneStableCivilDayKey() {
        val beforeFallback = PracticeDayIdentity.capture(
            Instant.parse("2026-11-01T06:30:00Z"),
            ZoneId.of("America/Chicago")
        )
        val afterFallback = PracticeDayIdentity.capture(
            Instant.parse("2026-11-01T07:30:00Z"),
            ZoneId.of("America/Chicago")
        )

        assertEquals("2026-11-01", beforeFallback.localDayKey)
        assertEquals(beforeFallback.localDayKey, afterFallback.localDayKey)
        assertNotEquals(beforeFallback.utcOffsetSeconds, afterFallback.utcOffsetSeconds)
    }
}
