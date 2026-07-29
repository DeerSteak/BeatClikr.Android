package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolyrhythmTimelineTest {

    @Test
    fun mt012_mt015_mt018_threeAgainstTwoMergesCoincidentOrigins() {
        val events = timeline(beats = 3, against = 2)
            .eventsIn(FrameRange(0, 48_000))
            .toList()

        assertEquals(listOf(0L, 16_000L, 24_000L, 32_000L), events.map { it.intendedFrame })
        assertEquals(listOf(0L, 1L, 2L, 3L), events.map { it.sequence.index })
        assertEquals(MusicalEventRole.POLYRHYTHM_BEAT, events.first().primary.role)
        assertEquals(MusicalEventRole.POLYRHYTHM_RHYTHM, events.first().secondary?.role)
        assertTrue(events.drop(1).all { it.secondary == null })
    }

    @Test
    fun completeCyclesPreserveIndependentVoiceIndicesAndMonotonicSequence() {
        val events = timeline(beats = 4, against = 3)
            .eventsIn(FrameRange(0, 144_001))
            .toList()

        assertEquals(13, events.size)
        assertEquals((0L..12L).toList(), events.map { it.sequence.index })
        assertEquals(listOf(0L, 1L, 2L), events.filter(::isCoincident).map { it.primary.position.cycleIndex })
        assertEquals(
            listOf(0, 1, 2, 0, 1, 2, 0),
            events.filter { it.primary.role == MusicalEventRole.POLYRHYTHM_BEAT }.map { it.primary.position.index }
        )
        assertEquals(
            listOf(0, 1, 2, 3, 0, 1, 2, 3, 0),
            events.flatMap { listOfNotNull(it.primary, it.secondary) }
                .filter { it.role == MusicalEventRole.POLYRHYTHM_RHYTHM }
                .map { it.position.index }
        )
    }

    @Test
    fun allRatiosEmitOneEventForEveryCoincidentFrame() {
        for (beats in PolyrhythmConfiguration.SUPPORTED_COUNT) {
            for (against in PolyrhythmConfiguration.SUPPORTED_COUNT) {
                val timeline = timeline(beats, against)
                val cycleFrames = 24_000L * against
                val events = timeline.eventsIn(FrameRange(0, cycleFrames)).toList()
                val expectedUnique = beats + against - greatestCommonDivisor(beats, against)

                assertEquals("$beats:$against", expectedUnique, events.size)
                assertEquals("$beats:$against", events.size, events.map { it.intendedFrame }.distinct().size)
            }
        }
    }

    @Test
    fun adjacentRangesAndNewOriginsPreserveOrResetIdentity() {
        val original = timeline(15, 14, SessionOrigin(SessionID(8), 12_000))
        val whole = original.eventsIn(FrameRange(12_000, 400_000)).toList()
        val split = original.eventsIn(FrameRange(12_000, 200_000)).toList() +
            original.eventsIn(FrameRange(200_000, 400_000)).toList()
        val restarted = timeline(15, 14, SessionOrigin(SessionID(9), 500_000))
            .eventsIn(FrameRange(500_000, 500_001))
            .single()

        assertEquals(whole, split)
        assertEquals(0L, restarted.sequence.index)
        assertEquals(0, restarted.primary.position.index)
        assertEquals(0, restarted.secondary?.position?.index)
        assertFalse(whole.first().sequence.sessionID == restarted.sequence.sessionID)
    }

    @Test
    fun muteDoesNotRemovePolyrhythmEventsOrPhase() {
        val audible = timeline(5, 7, muteMetronome = false)
            .eventsIn(FrameRange(0, 168_000))
            .toList()
        val muted = timeline(5, 7, muteMetronome = true)
            .eventsIn(FrameRange(0, 168_000))
            .toList()

        assertEquals(audible.map { it.intendedFrame }, muted.map { it.intendedFrame })
        assertEquals(audible.map { it.sequence }, muted.map { it.sequence })
        assertTrue(muted.all { it.muteMetronome })
    }

    private fun timeline(
        beats: Int,
        against: Int,
        origin: SessionOrigin = SessionOrigin(SessionID(1), 0),
        muteMetronome: Boolean = false
    ) = PolyrhythmTimeline(
        configuration = PolyrhythmConfiguration(
            bpm = ExactTempo.of(120),
            beats = beats,
            against = against,
            muteMetronome = muteMetronome
        ),
        sampleRate = 48_000,
        origin = origin
    )

    private fun isCoincident(event: FrameEvent): Boolean = event.secondary != null

    private tailrec fun greatestCommonDivisor(first: Int, second: Int): Int =
        if (second == 0) first else greatestCommonDivisor(second, first % second)
}
