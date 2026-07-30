package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.ExactTempo
import com.bfunkstudios.beatclikr.music.SoundRole
import com.bfunkstudios.beatclikr.music.FrameRangeEventConsumer
import com.bfunkstudios.beatclikr.music.FrameRangeEventSource
import com.bfunkstudios.beatclikr.music.MusicalEventRole
import com.bfunkstudios.beatclikr.music.PolyrhythmConfiguration
import com.bfunkstudios.beatclikr.music.PolyrhythmTimeline
import com.bfunkstudios.beatclikr.music.SessionID
import com.bfunkstudios.beatclikr.music.SessionOrigin
import com.bfunkstudios.beatclikr.music.StandardMetronomeConfiguration
import com.bfunkstudios.beatclikr.music.StandardMetronomeTimeline
import com.bfunkstudios.beatclikr.music.StandardSubdivision
import com.bfunkstudios.beatclikr.music.StandardTiming
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FramePcmRendererTest {
    @Test
    fun requestsEveryOutputRangeAndMixesAtExactFrameOffsets() {
        val source = RecordingEventSource(
            Event(3, SoundRole.BEAT),
            Event(7, SoundRole.RHYTHM)
        )
        val renderer = renderer(source, beat = shortArrayOf(10, 20), rhythm = shortArrayOf(3, 4))
        val output = ShortArray(10)

        assertEquals(FrameRenderResult.COMPLETE, renderer.render(0, output, output.size))

        assertEquals(0, source.lastStartFrame)
        assertEquals(10, source.lastEndFrame)
        assertArrayEquals(
            shortArrayOf(0, 0, 0, 10, 20, 0, 0, 3, 4, 0),
            output
        )
    }

    @Test
    fun carriesWaveformTailAcrossAdjacentBlocks() {
        val renderer = renderer(
            RecordingEventSource(Event(2, SoundRole.BEAT)),
            beat = shortArrayOf(1, 2, 3, 4),
            rhythm = shortArrayOf(9)
        )
        val first = ShortArray(4)
        val second = ShortArray(4)

        renderer.render(0, first, first.size)
        renderer.render(4, second, second.size)

        assertArrayEquals(shortArrayOf(0, 0, 1, 2), first)
        assertArrayEquals(shortArrayOf(3, 4, 0, 0), second)
    }

    @Test
    fun saturatesCoincidentAndOverlappingVoicesAfterSumming() {
        val source = RecordingEventSource(
            Event(0, SoundRole.BEAT, SoundRole.RHYTHM),
            Event(1, SoundRole.BEAT)
        )
        val renderer = renderer(
            source,
            beat = shortArrayOf(30_000, 30_000, -30_000),
            rhythm = shortArrayOf(20_000, -20_000, -20_000)
        )
        val output = ShortArray(4)

        renderer.render(0, output, output.size)

        assertArrayEquals(
            shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, -20_000, -30_000),
            output
        )
    }

    @Test
    fun mutedEventsDoNotConsumeVoicesOrProduceSamples() {
        val renderer = renderer(
            RecordingEventSource(Event(0, SoundRole.BEAT, muted = true)),
            beat = shortArrayOf(100),
            rhythm = shortArrayOf(50),
            maximumActiveVoices = 1
        )
        val output = ShortArray(1)

        assertEquals(FrameRenderResult.COMPLETE, renderer.render(0, output, 1))
        assertArrayEquals(shortArrayOf(0), output)
        assertEquals(0, renderer.renderedBeatEvents)
        assertEquals(0, renderer.renderedRhythmEvents)
    }

    @Test
    fun liveMuteOverrideAppliesWithoutRepublishingRenderer() {
        val renderer = renderer(
            RecordingEventSource(
                Event(0, SoundRole.BEAT),
                Event(1, SoundRole.BEAT)
            ),
            beat = shortArrayOf(100),
            rhythm = shortArrayOf(50)
        )
        val muted = ShortArray(1)
        val audible = ShortArray(1)

        renderer.setMuted(true)
        renderer.render(0, muted, 1)
        renderer.setMuted(false)
        renderer.render(1, audible, 1)

        assertArrayEquals(shortArrayOf(0), muted)
        assertArrayEquals(shortArrayOf(100), audible)
        assertEquals(1, renderer.renderedBeatEvents)
    }

    @Test
    fun countsAudibleRolesOnlyAfterSuccessfulBlock() {
        val renderer = renderer(
            RecordingEventSource(
                Event(0, SoundRole.BEAT, SoundRole.RHYTHM),
                Event(1, SoundRole.BEAT, muted = true)
            ),
            beat = shortArrayOf(1),
            rhythm = shortArrayOf(1)
        )

        renderer.render(0, ShortArray(2), 2)

        assertEquals(1, renderer.renderedBeatEvents)
        assertEquals(1, renderer.renderedRhythmEvents)
    }

    @Test
    fun failedBlockDoesNotCommitRoleCounters() {
        val renderer = renderer(
            RecordingEventSource(Event(0, SoundRole.BEAT, SoundRole.RHYTHM)),
            beat = shortArrayOf(1, 2),
            rhythm = shortArrayOf(1, 2),
            maximumActiveVoices = 1
        )

        renderer.render(0, ShortArray(1), 1)

        assertEquals(0, renderer.renderedBeatEvents)
        assertEquals(0, renderer.renderedRhythmEvents)
    }

    @Test
    fun successfulBlockPublishesFullEventMetadataToBoundedCapture() {
        val capture = RenderedEventRing(4)
        val renderer = FramePcmRenderer(
            StandardMetronomeTimeline(
                StandardMetronomeConfiguration(
                    bpm = ExactTempo.of(120),
                    timing = StandardTiming.Regular(StandardSubdivision.QUARTER)
                ),
                48_000,
                SessionOrigin(SessionID(9), 0)
            ),
            RenderWaveforms(shortArrayOf(1), shortArrayOf(1)),
            maximumActiveVoices = 4,
            eventCapture = capture
        )
        renderer.prepare(64)

        assertEquals(FrameRenderResult.COMPLETE, renderer.render(0, ShortArray(64), 64))
        val record = capture.drain(0).records.single()

        assertEquals(9L, record.sessionId)
        assertEquals(0L, record.eventSequence)
        assertEquals(MusicalEventRole.STANDARD, record.role)
        assertEquals(0L, record.intendedFrame)
    }

    @Test
    fun reportsCapacityAndSourceFailuresWithoutThrowing() {
        val full = renderer(
            RecordingEventSource(Event(0, SoundRole.BEAT, SoundRole.RHYTHM)),
            beat = shortArrayOf(1, 2),
            rhythm = shortArrayOf(1, 2),
            maximumActiveVoices = 1
        )
        val failedSource = renderer(PartiallyFailingEventSource, shortArrayOf(4), shortArrayOf(1))

        val capacityOutput = shortArrayOf(4)
        assertEquals(
            FrameRenderResult.VOICE_CAPACITY_EXCEEDED,
            full.render(0, capacityOutput, 1)
        )
        assertArrayEquals(shortArrayOf(0), capacityOutput)
        val failedOutput = shortArrayOf(9)
        assertEquals(
            FrameRenderResult.EVENT_SOURCE_FAILED,
            failedSource.render(0, failedOutput, 1)
        )
        assertArrayEquals(shortArrayOf(0), failedOutput)
    }

    @Test
    fun rejectsDiscontinuousBlocksAndResetDropsOldTails() {
        val renderer = renderer(
            RecordingEventSource(Event(0, SoundRole.BEAT)),
            beat = shortArrayOf(1, 2, 3, 4),
            rhythm = shortArrayOf(1)
        )
        renderer.render(0, ShortArray(2), 2)

        assertEquals(FrameRenderResult.INVALID_RANGE, renderer.render(8, ShortArray(2), 2))
        renderer.reset()
        val restarted = ShortArray(2)
        assertEquals(FrameRenderResult.COMPLETE, renderer.render(8, restarted, 2))
        assertArrayEquals(shortArrayOf(0, 0), restarted)
    }

    @Test
    fun discontinuousRangeDoesNotConsumeActiveTail() {
        val renderer = renderer(
            RecordingEventSource(Event(0, SoundRole.BEAT)),
            beat = shortArrayOf(1, 2, 3),
            rhythm = shortArrayOf(1)
        )
        renderer.render(0, ShortArray(1), 1)

        assertEquals(
            FrameRenderResult.INVALID_RANGE,
            renderer.render(Long.MAX_VALUE, ShortArray(1), 1)
        )
        val tail = ShortArray(2)
        renderer.render(1, tail, 2)
        assertArrayEquals(shortArrayOf(2, 3), tail)
    }

    @Test
    fun overflowingRangeIsRejectedBeforeEventSourceIsVisited() {
        val source = CountingEventSource()
        val renderer = renderer(source, shortArrayOf(1), shortArrayOf(1))
        renderer.reset()

        assertEquals(
            FrameRenderResult.INVALID_RANGE,
            renderer.render(Long.MAX_VALUE, ShortArray(1), 1)
        )
        assertEquals(0, source.visitCount)
    }

    @Test
    fun standardTimelinePreparedRenderAllocatesNoMemory() {
        val timeline = StandardMetronomeTimeline(
            configuration = StandardMetronomeConfiguration(
                bpm = ExactTempo.of(240),
                timing = StandardTiming.Regular(StandardSubdivision.SIXTEENTH)
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(SessionID(1), 0)
        )
        val renderer = renderer(timeline, shortArrayOf(1), shortArrayOf(1))
        assertPreparedRenderAllocatesNoMemory(renderer)
    }

    @Test
    fun polyrhythmTimelinePreparedRenderAllocatesNoMemory() {
        val timeline = PolyrhythmTimeline(
            configuration = PolyrhythmConfiguration(
                bpm = ExactTempo.of(173),
                beats = 5,
                against = 7
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(SessionID(2), 0)
        )
        val renderer = renderer(timeline, shortArrayOf(1), shortArrayOf(1))
        assertPreparedRenderAllocatesNoMemory(renderer)
    }

    @Test
    fun preparedRenderWithEventCaptureAllocatesNoMemory() {
        val timeline = StandardMetronomeTimeline(
            configuration = StandardMetronomeConfiguration(
                bpm = ExactTempo.of(240),
                timing = StandardTiming.Regular(StandardSubdivision.SIXTEENTH)
            ),
            sampleRate = 48_000,
            origin = SessionOrigin(SessionID(3), 0)
        )
        val renderer = FramePcmRenderer(
            timeline,
            RenderWaveforms(shortArrayOf(1), shortArrayOf(1)),
            maximumActiveVoices = 8,
            eventCapture = RenderedEventRing(512)
        ).also { it.prepare(64) }

        assertPreparedRenderAllocatesNoMemory(renderer)
    }

    private fun assertPreparedRenderAllocatesNoMemory(renderer: FramePcmRenderer) {
        val output = ShortArray(64)
        var startFrame = 0L
        repeat(500_000) {
            renderer.render(startFrame, output, output.size)
            startFrame += output.size
        }
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        assumeTrue(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        var minimumAllocatedBytes = Long.MAX_VALUE
        repeat(5) {
            val before = bean.currentThreadAllocatedBytes
            repeat(10_000) {
                renderer.render(startFrame, output, output.size)
                startFrame += output.size
            }
            minimumAllocatedBytes = minOf(
                minimumAllocatedBytes,
                bean.currentThreadAllocatedBytes - before
            )
        }

        assertEquals(0, minimumAllocatedBytes)
    }

    private fun renderer(
        source: FrameRangeEventSource,
        beat: ShortArray,
        rhythm: ShortArray,
        maximumActiveVoices: Int = 8
    ): FramePcmRenderer =
        FramePcmRenderer(
            eventSource = source,
            waveforms = RenderWaveforms(beat, rhythm),
            maximumActiveVoices = maximumActiveVoices
        ).also { it.prepare(64) }

    private data class Event(
        val frame: Long,
        val primary: SoundRole,
        val secondary: SoundRole? = null,
        val muted: Boolean = false
    )

    private class RecordingEventSource(vararg events: Event) : FrameRangeEventSource {
        private val events = events
        var lastStartFrame = -1L
        var lastEndFrame = -1L

        override fun visitEvents(
            startFrame: Long,
            endFrameExclusive: Long,
            consumer: FrameRangeEventConsumer
        ): Boolean {
            lastStartFrame = startFrame
            lastEndFrame = endFrameExclusive
            events.forEachIndexed { index, event ->
                if (event.frame in startFrame until endFrameExclusive) {
                    if (!consumer.accept(
                            1,
                            index.toLong(),
                            event.frame,
                            MusicalEventRole.STANDARD,
                            event.primary,
                            event.secondary?.let { MusicalEventRole.POLYRHYTHM_RHYTHM },
                            event.secondary,
                            event.muted
                        )
                    ) {
                        return true
                    }
                }
            }
            return true
        }
    }

    private object PartiallyFailingEventSource : FrameRangeEventSource {
        override fun visitEvents(
            startFrame: Long,
            endFrameExclusive: Long,
            consumer: FrameRangeEventConsumer
        ): Boolean {
            consumer.accept(
                1,
                0,
                startFrame,
                MusicalEventRole.STANDARD,
                SoundRole.BEAT,
                null,
                null,
                false
            )
            return false
        }
    }

    private class CountingEventSource : FrameRangeEventSource {
        var visitCount = 0

        override fun visitEvents(
            startFrame: Long,
            endFrameExclusive: Long,
            consumer: FrameRangeEventConsumer
        ): Boolean {
            visitCount++
            return true
        }
    }
}
