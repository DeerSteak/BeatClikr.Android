package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRangeEventSourceTest {
    @Test
    fun standardVisitorMatchesObjectTimeline() {
        val timeline = StandardMetronomeTimeline(
            configuration = StandardMetronomeConfiguration(
                bpm = ExactTempo.parse("137.5"),
                timing = StandardTiming.Regular(StandardSubdivision.TRIPLET),
                alternateSixteenth = true
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(SessionID(2), 12_000)
        )

        assertVisitorMatchesEvents(timeline, 12_000, 200_000)
    }

    @Test
    fun polyrhythmVisitorMatchesCoincidentAndIndependentVoices() {
        val timeline = PolyrhythmTimeline(
            configuration = PolyrhythmConfiguration(
                bpm = ExactTempo.of(173),
                beats = 5,
                against = 7
            ),
            sampleRate = 44_100,
            origin = SessionOrigin(SessionID(3), 8_000)
        )

        assertVisitorMatchesEvents(timeline, 8_000, 300_000)
    }

    @Test
    fun adjacentRenderBlocksMatchOneWholeRange() {
        val timeline = StandardMetronomeTimeline(
            configuration = StandardMetronomeConfiguration(
                bpm = ExactTempo.of(240),
                timing = StandardTiming.Regular(StandardSubdivision.SIXTEENTH),
                muteMetronome = true
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(SessionID(4), 0)
        )
        val whole = visit(timeline, 0, 12_800)
        val blocks = mutableListOf<VisitedEvent>()

        var startFrame = 0L
        while (startFrame < 12_800) {
            timeline.visitEvents(startFrame, startFrame + 64) {
                    _, _, frame, _, primary, _, secondary, muted ->
                blocks += VisitedEvent(frame, primary, secondary, muted)
                true
            }
            startFrame += 64
        }

        assertEquals(whole, blocks)
        assertEquals(true, blocks.all { it.muted })
    }

    @Test
    fun consumerAbortStopsCurrentRangeImmediately() {
        val timeline = StandardMetronomeTimeline(
            configuration = StandardMetronomeConfiguration(
                bpm = ExactTempo.of(240),
                timing = StandardTiming.Regular(StandardSubdivision.SIXTEENTH)
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(SessionID(5), 0)
        )
        var visits = 0

        timeline.visitEvents(0, 30_000) { _, _, _, _, _, _, _, _ ->
            visits++
            false
        }

        assertEquals(1, visits)
    }

    private fun assertVisitorMatchesEvents(
        timeline: FrameEventTimeline,
        startFrame: Long,
        endFrameExclusive: Long
    ) {
        val expected = timeline.eventsIn(FrameRange(startFrame, endFrameExclusive))
            .map {
                VisitedEvent(
                    frame = it.intendedFrame,
                    primary = it.primary.soundRole,
                    secondary = it.secondary?.soundRole,
                    muted = it.muteMetronome
                )
            }
            .toList()
        val visited = mutableListOf<VisitedEvent>()

        timeline.visitEvents(startFrame, endFrameExclusive) {
                _, _, frame, _, primary, _, secondary, muted ->
            visited += VisitedEvent(frame, primary, secondary, muted)
            true
        }

        assertEquals(expected, visited)
    }

    private fun visit(
        timeline: FrameEventTimeline,
        startFrame: Long,
        endFrameExclusive: Long
    ): List<VisitedEvent> {
        val visited = mutableListOf<VisitedEvent>()
        timeline.visitEvents(startFrame, endFrameExclusive) {
                _, _, frame, _, primary, _, secondary, muted ->
            visited += VisitedEvent(frame, primary, secondary, muted)
            true
        }
        return visited
    }

    private data class VisitedEvent(
        val frame: Long,
        val primary: SoundRole,
        val secondary: SoundRole?,
        val muted: Boolean
    )
}
