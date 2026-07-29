package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.SoundRole
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    }

    @Test
    fun reportsCapacityAndSourceFailuresWithoutThrowing() {
        val full = renderer(
            RecordingEventSource(Event(0, SoundRole.BEAT, SoundRole.RHYTHM)),
            beat = shortArrayOf(1, 2),
            rhythm = shortArrayOf(1, 2),
            maximumActiveVoices = 1
        )
        val failedSource = renderer(FailingEventSource, shortArrayOf(1), shortArrayOf(1))

        assertEquals(
            FrameRenderResult.VOICE_CAPACITY_EXCEEDED,
            full.render(0, ShortArray(1), 1)
        )
        assertEquals(
            FrameRenderResult.EVENT_SOURCE_FAILED,
            failedSource.render(0, ShortArray(1), 1)
        )
    }

    @Test
    fun preparedHappyPathAllocatesNoRenderThreadMemory() {
        val renderer = renderer(PeriodicEventSource, shortArrayOf(1), shortArrayOf(1))
        val output = ShortArray(64)
        repeat(100_000) { renderer.render(64_000L, output, output.size) }
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        assertTrue(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val before = bean.currentThreadAllocatedBytes

        repeat(10_000) { renderer.render(64_000L, output, output.size) }

        assertEquals(0, bean.currentThreadAllocatedBytes - before)
    }

    private fun renderer(
        source: RenderEventSource,
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

    private class RecordingEventSource(vararg events: Event) : RenderEventSource {
        private val events = events
        var lastStartFrame = -1L
        var lastEndFrame = -1L

        override fun visitEvents(
            startFrame: Long,
            endFrameExclusive: Long,
            consumer: RenderEventConsumer
        ): Boolean {
            lastStartFrame = startFrame
            lastEndFrame = endFrameExclusive
            events.forEach { event ->
                if (event.frame in startFrame until endFrameExclusive) {
                    if (!consumer.accept(event.frame, event.primary, event.secondary, event.muted)) {
                        return true
                    }
                }
            }
            return true
        }
    }

    private object PeriodicEventSource : RenderEventSource {
        override fun visitEvents(
            startFrame: Long,
            endFrameExclusive: Long,
            consumer: RenderEventConsumer
        ): Boolean = consumer.accept(startFrame, SoundRole.BEAT, null, false)
    }

    private object FailingEventSource : RenderEventSource {
        override fun visitEvents(
            startFrame: Long,
            endFrameExclusive: Long,
            consumer: RenderEventConsumer
        ): Boolean = false
    }
}
