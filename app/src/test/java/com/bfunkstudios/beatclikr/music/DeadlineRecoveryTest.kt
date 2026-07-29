package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadlineRecoveryTest {

    @Test
    fun mt030_mt031_multiEventStallDropsEveryExpiredEventWithoutCatchUpBurst() {
        val timeline = standardTimeline()
        val initial = DeadlineRecoveryState.atOrigin(timeline)

        val result = DeadlineRecovery.process(
            timeline,
            initial,
            FrameRange(60_000, 72_000)
        )

        assertEquals(listOf(60_000L), result.events.map { it.intendedFrame })
        assertEquals(listOf(5L), result.events.map { it.sequence.index })
        assertEquals(5L, result.state.diagnostics.deadlineMisses)
        assertEquals(5L, result.state.diagnostics.droppedEvents)
        assertEquals(1L, result.state.diagnostics.committedEvents)
        assertEquals(1L, result.state.diagnostics.recoveryWindows)
    }

    @Test
    fun firstFutureEventAlwaysDerivesFromTheOriginalSessionOrigin() {
        val timeline = StandardMetronomeTimeline(
            configuration = StandardMetronomeConfiguration(
                bpm = ExactTempo.parse("137.5"),
                timing = StandardTiming.Regular(StandardSubdivision.TRIPLET)
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(SessionID(9), 10_000)
        )
        val expected = timeline.eventsIn(FrameRange(200_000, 240_000)).toList()
        val result = DeadlineRecovery.process(
            timeline,
            DeadlineRecoveryState.atOrigin(timeline),
            FrameRange(200_000, 240_000)
        )

        assertEquals(expected, result.events)
        assertEquals(10_000L, result.state.originFrame)
        assertEquals(SessionID(9), result.state.sessionID)
        assertTrue(result.events.all { it.intendedFrame >= 200_000 })
    }

    @Test
    fun adjacentAndOverlappingWindowsCannotDuplicateFramesOrSequences() {
        val timeline = standardTimeline()
        var state = DeadlineRecoveryState.atOrigin(timeline)
        val committed = mutableListOf<FrameEvent>()

        listOf(
            FrameRange(0, 30_000),
            FrameRange(24_000, 54_000),
            FrameRange(54_000, 90_000)
        ).forEach { window ->
            val result = DeadlineRecovery.process(timeline, state, window)
            committed += result.events
            state = result.state
        }

        assertEquals(committed.size, committed.map { it.intendedFrame }.distinct().size)
        assertEquals(committed.size, committed.map { it.sequence }.distinct().size)
        assertEquals((0L..7L).toList(), committed.map { it.sequence.index })
        assertEquals(0L, state.diagnostics.deadlineMisses)
    }

    @Test
    fun polyrhythmCoincidenceCountsAsOneDroppedFrameEvent() {
        val timeline = PolyrhythmTimeline(
            configuration = PolyrhythmConfiguration(
                bpm = ExactTempo.of(120),
                beats = 3,
                against = 2
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(SessionID(4), 0)
        )
        val result = DeadlineRecovery.process(
            timeline,
            DeadlineRecoveryState.atOrigin(timeline),
            FrameRange(1, 16_001)
        )

        assertEquals(1L, result.state.diagnostics.deadlineMisses)
        assertEquals(1L, result.state.diagnostics.droppedEvents)
        assertEquals(listOf(16_000L), result.events.map { it.intendedFrame })
        assertEquals(TimelineMode.POLYRHYTHM, result.state.diagnostics.mode)
        assertEquals(SessionID(4), result.state.diagnostics.sessionID)
    }

    @Test
    fun repeatedStallsAccumulatePerSessionAndModeDiagnostics() {
        val timeline = standardTimeline(sessionID = SessionID(12))
        var state = DeadlineRecoveryState.atOrigin(timeline)

        listOf(
            FrameRange(24_000, 36_000),
            FrameRange(72_000, 84_000),
            FrameRange(120_000, 132_000)
        ).forEach { window ->
            state = DeadlineRecovery.process(timeline, state, window).state
        }

        assertEquals(SessionID(12), state.diagnostics.sessionID)
        assertEquals(TimelineMode.STANDARD, state.diagnostics.mode)
        assertEquals(8L, state.diagnostics.deadlineMisses)
        assertEquals(8L, state.diagnostics.droppedEvents)
        assertEquals(3L, state.diagnostics.committedEvents)
        assertEquals(3L, state.diagnostics.recoveryWindows)
    }

    @Test
    fun staleSessionModeAndMovedOriginAreRejected() {
        val timeline = standardTimeline()
        val state = DeadlineRecoveryState.atOrigin(timeline)

        assertThrows(IllegalArgumentException::class.java) {
            DeadlineRecovery.process(
                standardTimeline(sessionID = SessionID(2)),
                state,
                FrameRange(0, 12_000)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeadlineRecovery.process(
                standardTimeline(originFrame = 1),
                state,
                FrameRange(1, 12_001)
            )
        }
        val polyrhythm = PolyrhythmTimeline(
            PolyrhythmConfiguration(ExactTempo.of(120), 3, 2),
            48_000,
            SessionOrigin(SessionID(1), 0)
        )
        assertThrows(IllegalArgumentException::class.java) {
            DeadlineRecovery.process(polyrhythm, state, FrameRange(0, 12_000))
        }
    }

    private fun standardTimeline(
        sessionID: SessionID = SessionID(1),
        originFrame: Long = 0
    ) = StandardMetronomeTimeline(
        configuration = StandardMetronomeConfiguration(
            bpm = ExactTempo.of(120),
            timing = StandardTiming.Regular(StandardSubdivision.EIGHTH)
        ),
        sampleRate = 48_000,
        origin = SessionOrigin(sessionID, originFrame)
    )
}
