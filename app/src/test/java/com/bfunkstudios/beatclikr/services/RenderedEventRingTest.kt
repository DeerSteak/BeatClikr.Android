package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.MusicalEventRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun publishedRecordFieldsAreVisibleAcrossThreads() {
        val ring = RenderedEventRing(1)
        val published = CountDownLatch(1)
        val producer = Thread {
            ring.record(9, 27, MusicalEventRole.POLYRHYTHM_RHYTHM, 144, true, 3)
            published.countDown()
        }
        producer.start()
        assertEquals(true, published.await(2, TimeUnit.SECONDS))

        val record = ring.drain(0).records.single()
        producer.join()

        assertEquals(9, record.sessionId)
        assertEquals(27, record.eventSequence)
        assertEquals(MusicalEventRole.POLYRHYTHM_RHYTHM, record.role)
        assertEquals(144, record.intendedFrame)
        assertTrue(record.muted)
        assertEquals(3, record.roleIndex)
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

    @Test
    fun preservesAuthoritativeRoleIndex() {
        val ring = RenderedEventRing(2)
        ring.record(
            1,
            4,
            MusicalEventRole.POLYRHYTHM_RHYTHM,
            100,
            false,
            roleIndex = 2
        )

        assertEquals(2, ring.drain(0).records.single().roleIndex)
    }
}
