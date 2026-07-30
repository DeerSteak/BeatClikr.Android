package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.MusicalEventRole
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderedEventRingTest {
    @Test
    fun drainsOrderedRecordsAfterCallerCursor() {
        val ring = RenderedEventRing(4)
        ring.record(7, 10, MusicalEventRole.STANDARD, 48_000, false)
        ring.record(7, 11, MusicalEventRole.STANDARD, 60_000, true)

        val batch = ring.drain(0)

        assertEquals(listOf(10L, 11L), batch.records.map { it.eventSequence })
        assertEquals(2, batch.nextCaptureSequence)
        assertEquals(0, batch.droppedRecords)
    }

    @Test
    fun reportsOverwrittenRecordsWithoutGrowing() {
        val ring = RenderedEventRing(2)
        repeat(4) { index ->
            ring.record(
                3,
                index.toLong(),
                MusicalEventRole.POLYRHYTHM_BEAT,
                index.toLong(),
                false
            )
        }

        val batch = ring.drain(0)

        assertEquals(listOf(2L, 3L), batch.records.map { it.eventSequence })
        assertEquals(2, batch.droppedRecords)
        assertEquals(4, batch.nextCaptureSequence)
    }
}
