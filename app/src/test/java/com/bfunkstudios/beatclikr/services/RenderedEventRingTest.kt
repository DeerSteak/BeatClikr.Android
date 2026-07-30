package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.MusicalEventRole
import org.junit.Assert.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @Test
    fun overwriteDuringDrainCannotPublishTornRecord() {
        val ring = RenderedEventRing(1)
        ring.record(1, 10, MusicalEventRole.STANDARD, 100, false)
        val validated = CountDownLatch(1)
        val overwritten = CountDownLatch(1)
        val producer = Thread {
            assertEquals(true, validated.await(2, TimeUnit.SECONDS))
            ring.record(2, 20, MusicalEventRole.STANDARD, 200, false)
            overwritten.countDown()
        }
        producer.start()

        val batch = ring.drain(0) {
            validated.countDown()
            assertEquals(true, overwritten.await(2, TimeUnit.SECONDS))
        }
        producer.join()

        assertEquals(emptyList<RenderedFrameEvent>(), batch.records)
        assertEquals(1, batch.droppedRecords)
    }

    @Test
    fun explicitProducerDropIsObservable() {
        val ring = RenderedEventRing(4)
        ring.record(1, 0, MusicalEventRole.STANDARD, 0, false)
        ring.drop()
        ring.record(1, 2, MusicalEventRole.STANDARD, 2, false)

        val batch = ring.drain(0)

        assertEquals(listOf(0L, 2L), batch.records.map { it.eventSequence })
        assertEquals(1, batch.droppedRecords)
    }
}
