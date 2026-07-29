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

        timeline.visitEvents(startFrame, endFrameExclusive) { frame, primary, secondary, muted ->
            visited += VisitedEvent(frame, primary, secondary, muted)
            true
        }

        assertEquals(expected, visited)
    }

    private data class VisitedEvent(
        val frame: Long,
        val primary: SoundRole,
        val secondary: SoundRole?,
        val muted: Boolean
    )
}
