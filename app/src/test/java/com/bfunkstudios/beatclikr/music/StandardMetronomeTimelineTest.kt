package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardMetronomeTimelineTest {

    @Test
    fun mt004_mt007_mt011_eachRegularGrooveStartsAtTickZeroAndFillsOneQuarter() {
        StandardSubdivision.entries.forEach { subdivision ->
            val timeline = regularTimeline(subdivision = subdivision)
            val events = timeline.eventsIn(FrameRange(12_000, 36_000)).toList()

            assertEquals(subdivision.subdivisions, events.size)
            assertEquals(List(subdivision.subdivisions) { it.toLong() }, events.map { it.sequence.index })
            assertEquals(List(subdivision.subdivisions) { it }, events.map { it.primary.position.index })
            assertEquals(BeatIdentity.BEAT, events.first().primary.beatIdentity)
            assertEquals(SoundRole.BEAT, events.first().primary.soundRole)
            events.drop(1).forEach {
                assertEquals(BeatIdentity.SUBDIVISION, it.primary.beatIdentity)
                assertEquals(SoundRole.RHYTHM, it.primary.soundRole)
            }
        }
    }

    @Test
    fun mt006_mt008_additivePatternPreservesEveryAccentAndSoundRole() {
        val accents = listOf(true, false, false, true, false, true, false)
        val timeline = StandardMetronomeTimeline(
            configuration = StandardMetronomeConfiguration(
                bpm = ExactTempo.of(120),
                timing = StandardTiming.Additive(
                    stepUnit = AdditiveStepUnit.EIGHTH,
                    accents = AccentPattern.of(accents)
                )
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(SessionID(3), 0)
        )
        val cycleEnd = 7L * 12_000
        val events = timeline.eventsIn(FrameRange(0, cycleEnd)).toList()

        assertEquals(accents.size, events.size)
        assertEquals(
            accents.map { if (it) BeatIdentity.ACCENT else BeatIdentity.SUBDIVISION },
            events.map { it.primary.beatIdentity }
        )
        assertEquals(
            accents.map { if (it) SoundRole.BEAT else SoundRole.RHYTHM },
            events.map { it.primary.soundRole }
        )
    }

    @Test
    fun mt009_alternateSixteenthChangesSoundWithoutChangingBeatIdentity() {
        val timeline = regularTimeline(
            subdivision = StandardSubdivision.SIXTEENTH,
            alternateSixteenth = true
        )
        val events = timeline.eventsIn(FrameRange(12_000, 36_000)).toList()

        assertEquals(
            listOf(SoundRole.BEAT, SoundRole.RHYTHM, SoundRole.BEAT, SoundRole.RHYTHM),
            events.map { it.primary.soundRole }
        )
        assertEquals(
            listOf(BeatIdentity.BEAT, BeatIdentity.SUBDIVISION, BeatIdentity.SUBDIVISION, BeatIdentity.SUBDIVISION),
            events.map { it.primary.beatIdentity }
        )
    }

    @Test
    fun mt010_muteRemainsAnOutputAttributeWithoutRemovingEventsOrPhase() {
        val audible = regularTimeline(muteMetronome = false)
            .eventsIn(FrameRange(12_000, 60_000))
            .toList()
        val muted = regularTimeline(muteMetronome = true)
            .eventsIn(FrameRange(12_000, 60_000))
            .toList()

        assertEquals(audible.map { it.intendedFrame }, muted.map { it.intendedFrame })
        assertEquals(audible.map { it.sequence }, muted.map { it.sequence })
        assertTrue(audible.none { it.muteMetronome })
        assertTrue(muted.all { it.muteMetronome })
    }

    @Test
    fun adjacentAndOverlappingRangesNeverChangeEventIdentityOrCreateBoundaryDuplicates() {
        val timeline = regularTimeline(
            bpm = ExactTempo.parse("137.5"),
            subdivision = StandardSubdivision.TRIPLET
        )
        val whole = timeline.eventsIn(FrameRange(12_000, 200_000)).toList()
        val first = timeline.eventsIn(FrameRange(12_000, 100_000)).toList()
        val second = timeline.eventsIn(FrameRange(100_000, 200_000)).toList()
        val overlap = timeline.eventsIn(FrameRange(80_000, 140_000)).toList()

        assertEquals(whole, first + second)
        assertEquals(
            whole.filter { it.intendedFrame in 80_000L until 140_000L },
            overlap
        )
        assertEquals(whole.size, whole.map { it.intendedFrame }.distinct().size)
    }

    @Test
    fun tb001_twelveHourDenseTimelineRetainsAbsoluteSequenceAndFrame() {
        val originFrame = 12_000L
        val timeline = regularTimeline(
            bpm = ExactTempo.of(240),
            subdivision = StandardSubdivision.SIXTEENTH,
            originFrame = originFrame
        )
        val intervalIndex = 12L * 60 * 240 * 4
        val expectedFrame = originFrame + intervalIndex * 3_000
        val event = timeline.eventsIn(
            FrameRange(expectedFrame, expectedFrame + 1)
        ).single()

        assertEquals(intervalIndex, event.sequence.index)
        assertEquals(expectedFrame, event.intendedFrame)
        assertEquals(intervalIndex / 4, event.primary.position.cycleIndex)
        assertEquals(0, event.primary.position.index)
    }

    @Test
    fun mt014_newSessionAndOriginResetPhaseToTickZero() {
        val first = regularTimeline(originFrame = 12_000, sessionID = SessionID(1))
            .eventsIn(FrameRange(12_000, 12_001))
            .single()
        val restarted = regularTimeline(originFrame = 90_000, sessionID = SessionID(2))
            .eventsIn(FrameRange(90_000, 90_001))
            .single()

        assertEquals(0L, first.sequence.index)
        assertEquals(0L, restarted.sequence.index)
        assertEquals(0, first.primary.position.index)
        assertEquals(0, restarted.primary.position.index)
        assertFalse(first.sequence.sessionID == restarted.sequence.sessionID)
    }

    @Test
    fun invalidFrameRangesFailBeforeGeneration() {
        assertThrows(IllegalArgumentException::class.java) { FrameRange(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { FrameRange(2, 1) }
    }

    private fun regularTimeline(
        bpm: ExactTempo = ExactTempo.of(120),
        subdivision: StandardSubdivision = StandardSubdivision.QUARTER,
        alternateSixteenth: Boolean = false,
        muteMetronome: Boolean = false,
        originFrame: Long = 12_000,
        sessionID: SessionID = SessionID(1)
    ): StandardMetronomeTimeline =
        StandardMetronomeTimeline(
            configuration = StandardMetronomeConfiguration(
                bpm = bpm,
                timing = StandardTiming.Regular(subdivision),
                alternateSixteenth = alternateSixteenth,
                muteMetronome = muteMetronome
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(sessionID, originFrame)
        )
}
