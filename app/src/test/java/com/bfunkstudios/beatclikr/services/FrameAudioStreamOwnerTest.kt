package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.ExactTempo
import com.bfunkstudios.beatclikr.music.SessionID
import com.bfunkstudios.beatclikr.music.SessionOrigin
import com.bfunkstudios.beatclikr.music.StandardMetronomeConfiguration
import com.bfunkstudios.beatclikr.music.StandardMetronomeTimeline
import com.bfunkstudios.beatclikr.music.StandardSubdivision
import com.bfunkstudios.beatclikr.music.StandardTiming
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameAudioStreamOwnerTest {
    @Test
    fun obtainedFactsCreateAndPrepareRendererOnce() {
        val backend = FakeBackend()
        val renderer = FakeRenderer()
        val owner = FrameAudioStreamOwner(backend)
        var factoryCalls = 0
        var factoryProperties: AudioBackendStreamProperties? = null

        val obtained = owner.open(REQUEST, { properties ->
            factoryCalls++
            factoryProperties = properties
            PublishedPcmFrameRenderer(renderer)
        }, {})

        assertSame(backend.properties, obtained)
        assertSame(backend.properties, factoryProperties)
        assertEquals(1, factoryCalls)
        assertEquals(backend.properties.burstFrames, renderer.preparedFrames)
    }

    @Test
    fun rendersAbsoluteRangesAndCompletesPartialWritesInFrameUnits() {
        val backend = FakeBackend().apply { writeResults.addAll(listOf(2, 2)) }
        val renderer = FakeRenderer()
        val owner = openedOwner(backend, renderer)

        assertTrue(owner.start(100))
        assertEquals(FrameStreamRenderResult.COMPLETE, owner.renderNextBlock())

        assertEquals(listOf(100L), renderer.renderStarts)
        assertEquals(listOf(0, 2), backend.writeOffsets)
        assertEquals(listOf(100L, 102L), backend.writeStarts)
        assertEquals(104, owner.nextFrame)
    }

    @Test
    fun failedPartialWriteOwnsWrittenFramesAndDropsRenderedTails() {
        val backend = FakeBackend().apply { writeResults.addAll(listOf(2, 0)) }
        val renderer = FakeRenderer()
        val owner = openedOwner(backend, renderer)
        owner.start(40)

        assertEquals(FrameStreamRenderResult.WRITE_FAILED, owner.renderNextBlock())

        assertEquals(42, owner.nextFrame)
        assertEquals(2, renderer.resetCount)
        assertEquals(FrameStreamRenderResult.NOT_RUNNING, owner.renderNextBlock())
    }

    @Test
    fun startRenderFailureAndStopResetRenderer() {
        val backend = FakeBackend()
        val renderer = FakeRenderer().apply {
            renderResult = FrameRenderResult.EVENT_SOURCE_FAILED
        }
        val owner = openedOwner(backend, renderer)

        assertTrue(owner.start(8))
        assertEquals(FrameStreamRenderResult.RENDER_FAILED, owner.renderNextBlock())
        assertTrue(owner.stop())
        assertTrue(owner.stop())

        assertEquals(3, renderer.resetCount)
        assertEquals(1, backend.stopCount)
        assertTrue(backend.writeStarts.isEmpty())
    }

    @Test
    fun failedStartResetsAndDoesNotRun() {
        val backend = FakeBackend().apply { startSucceeds = false }
        val renderer = FakeRenderer()
        val owner = openedOwner(backend, renderer)

        assertFalse(owner.start(0))

        assertEquals(2, renderer.resetCount)
        assertEquals(FrameStreamRenderResult.NOT_RUNNING, owner.renderNextBlock())
    }

    @Test
    fun invalidRendererPublicationBecomesTypedOpenFailure() {
        val backend = FakeBackend()
        val failures = mutableListOf<AudioBackendFailure>()
        val owner = FrameAudioStreamOwner(backend)

        val obtained = owner.open(
            REQUEST,
            { throw IllegalArgumentException("invalid domain input") },
            failures::add
        )

        assertEquals(null, obtained)
        assertEquals(AudioBackendFailureCode.INVALID_CONFIGURATION, failures.single().code)
        assertEquals(1, backend.stopCount)
    }

    @Test
    fun ownerRejectedOperationsReportToResponsibleCaller() {
        val backend = FakeBackend()
        val renderer = FakeRenderer()
        val failures = mutableListOf<AudioBackendFailure>()
        val refusedFailures = mutableListOf<AudioBackendFailure>()
        val owner = FrameAudioStreamOwner(backend)
        owner.open(REQUEST, { PublishedPcmFrameRenderer(renderer) }, failures::add)

        assertEquals(
            null,
            owner.open(
                REQUEST,
                { PublishedPcmFrameRenderer(renderer) },
                refusedFailures::add
            )
        )
        assertFalse(owner.start(-1))
        assertEquals(FrameStreamRenderResult.NOT_RUNNING, owner.renderNextBlock())

        assertEquals(
            listOf(
                AudioBackendOperation.START,
                AudioBackendOperation.RENDER
            ),
            failures.map { it.operation }
        )
        assertEquals(AudioBackendOperation.OPEN, refusedFailures.single().operation)
    }

    @Test
    fun resyncResetsFrameOwnershipWithoutReopeningStream() {
        val backend = FakeBackend()
        val renderer = FakeRenderer()
        val owner = openedOwner(backend, renderer)
        owner.start(10)
        owner.renderNextBlock()

        assertTrue(owner.resync(1_000))
        assertEquals(FrameStreamRenderResult.COMPLETE, owner.renderNextBlock())

        assertEquals(listOf(10L, 1_000L), renderer.renderStarts)
        assertEquals(1, backend.openCount)
        assertEquals(2, renderer.resetCount)
    }

    @Test
    fun resyncRequiresPriorSuccessfulStartAndRecoversHaltedLoop() {
        val backend = FakeBackend().apply { writeResults.addAll(listOf(0, 4)) }
        val renderer = FakeRenderer()
        val failures = mutableListOf<AudioBackendFailure>()
        val owner = FrameAudioStreamOwner(backend)
        owner.open(REQUEST, { PublishedPcmFrameRenderer(renderer) }, failures::add)

        assertFalse(owner.resync(0))
        assertEquals(AudioBackendOperation.RESYNC, failures.single().operation)
        assertEquals(0, backend.startCount)

        assertTrue(owner.start(20))
        assertEquals(FrameStreamRenderResult.WRITE_FAILED, owner.renderNextBlock())
        assertTrue(owner.resync(100))
        assertEquals(FrameStreamRenderResult.COMPLETE, owner.renderNextBlock())
        assertEquals(1, backend.startCount)
        assertEquals(104, owner.nextFrame)
    }

    @Test
    fun failedStopIsReportedOnceByBackendContract() {
        val backend = FakeBackend().apply { stopSucceeds = false }
        val failures = mutableListOf<AudioBackendFailure>()
        val owner = FrameAudioStreamOwner(backend)
        owner.open(
            REQUEST,
            { PublishedPcmFrameRenderer(FakeRenderer()) },
            failures::add
        )

        assertFalse(owner.stop())

        assertEquals(1, failures.size)
        assertEquals(AudioBackendOperation.STOP, failures.single().operation)
    }

    @Test
    fun deadlineRecoveryResyncDropsExpiredEventsAndOldWaveformTailsTogether() {
        val backend = FakeBackend()
        val timeline = StandardMetronomeTimeline(
            StandardMetronomeConfiguration(
                ExactTempo.of(120),
                StandardTiming.Regular(StandardSubdivision.EIGHTH)
            ),
            48_000,
            SessionOrigin(SessionID(1), 0)
        )
        val recovery = TimelineFrameStreamRecovery(timeline)
        val renderer = FramePcmRenderer(
            timeline,
            RenderWaveforms(
                beat = shortArrayOf(1, 2, 3, 4, 5, 6),
                rhythm = shortArrayOf(1, 2, 3, 4, 5, 6)
            ),
            maximumActiveVoices = 4
        )
        val owner = FrameAudioStreamOwner(backend)
        owner.open(
            REQUEST,
            { PublishedPcmFrameRenderer(renderer, recovery) },
            {}
        )
        owner.start(0)
        owner.renderNextBlock()

        assertTrue(owner.resync(24_000))
        owner.renderNextBlock()

        assertArrayEquals(shortArrayOf(1, 2, 3, 4), backend.lastWrittenSamples)
        assertEquals(1, recovery.snapshot.diagnostics.droppedEvents)
        assertEquals(24_004, owner.nextFrame)
    }

    @Test
    fun deadlineRecoveryRejectsBackwardResyncWithoutMovingOwnership() {
        val backend = FakeBackend()
        val renderer = FakeRenderer()
        val timeline = StandardMetronomeTimeline(
            StandardMetronomeConfiguration(
                ExactTempo.of(120),
                StandardTiming.Regular(StandardSubdivision.EIGHTH)
            ),
            48_000,
            SessionOrigin(SessionID(2), 0)
        )
        val recovery = TimelineFrameStreamRecovery(timeline)
        val owner = FrameAudioStreamOwner(backend)
        owner.open(
            REQUEST,
            { PublishedPcmFrameRenderer(renderer, recovery) },
            {}
        )
        owner.start(0)
        owner.renderNextBlock()

        assertFalse(owner.resync(2))

        assertEquals(4, owner.nextFrame)
        assertEquals(1, renderer.resetCount)
    }

    @Test
    fun preparedOwnerRenderAllocatesNoMemory() {
        val owner = FrameAudioStreamOwner(AllocationFreeBackend)
        owner.open(
            REQUEST,
            { PublishedPcmFrameRenderer(AllocationFreeRenderer) },
            {}
        )
        owner.start(0)
        repeat(100_000) { owner.renderNextBlock() }
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        assumeTrue(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        var minimumAllocatedBytes = Long.MAX_VALUE
        repeat(5) {
            val before = bean.currentThreadAllocatedBytes
            repeat(10_000) { owner.renderNextBlock() }
            minimumAllocatedBytes = minOf(
                minimumAllocatedBytes,
                bean.currentThreadAllocatedBytes - before
            )
        }

        assertEquals(0, minimumAllocatedBytes)
    }

    private fun openedOwner(
        backend: FakeBackend,
        renderer: FakeRenderer
    ): FrameAudioStreamOwner =
        FrameAudioStreamOwner(backend).also { owner ->
            owner.open(REQUEST, { PublishedPcmFrameRenderer(renderer) }, {})
        }

    private class FakeRenderer : PcmFrameRenderer {
        var preparedFrames = 0
        var resetCount = 0
        var renderResult = FrameRenderResult.COMPLETE
        val renderStarts = mutableListOf<Long>()

        override fun prepare(maximumBlockFrames: Int) {
            preparedFrames = maximumBlockFrames
        }

        override fun reset() {
            resetCount++
        }

        override fun render(
            startFrame: Long,
            output: ShortArray,
            frameCount: Int
        ): FrameRenderResult {
            renderStarts += startFrame
            return renderResult
        }
    }

    private class FakeBackend : AudioRenderBackend {
        val properties = AudioBackendStreamProperties(
            sampleRate = 48_000,
            channelCount = 2,
            burstFrames = 4,
            bufferFrames = 8,
            performanceMode = AudioBackendPerformanceMode.LOW_LATENCY
        )
        val writeResults = ArrayDeque<Int>()
        val writeOffsets = mutableListOf<Int>()
        val writeStarts = mutableListOf<Long>()
        var startSucceeds = true
        var stopSucceeds = true
        var stopCount = 0
        var openCount = 0
        var startCount = 0
        var lastWrittenSamples = ShortArray(0)
        private var failureSink = AudioBackendFailureSink {}

        override fun open(
            request: AudioBackendOpenRequest,
            failureSink: AudioBackendFailureSink
        ): AudioBackendStreamProperties {
            openCount++
            this.failureSink = failureSink
            return properties
        }

        override fun start(): Boolean {
            startCount++
            return startSucceeds
        }

        override fun render(
            monoPcm: ShortArray,
            frameOffset: Int,
            frameCount: Int,
            startFrame: Long
        ): Int {
            writeOffsets += frameOffset
            writeStarts += startFrame
            lastWrittenSamples = monoPcm.copyOfRange(
                frameOffset,
                frameOffset + frameCount
            )
            return if (writeResults.isEmpty()) frameCount else writeResults.removeFirst()
        }

        override fun stop(): Boolean {
            stopCount++
            if (!stopSucceeds) {
                failureSink.report(
                    AudioBackendFailure(
                        AudioBackendOperation.STOP,
                        AudioBackendFailureCode.INTERNAL_ERROR
                    )
                )
            }
            return stopSucceeds
        }

        override fun timestamp(destination: AudioFrameTimestamp): Boolean = false
    }

    private companion object {
        val REQUEST = AudioBackendOpenRequest(44_100, 1, 16)

        object AllocationFreeRenderer : PcmFrameRenderer {
            override fun prepare(maximumBlockFrames: Int) = Unit
            override fun reset() = Unit
            override fun render(
                startFrame: Long,
                output: ShortArray,
                frameCount: Int
            ): FrameRenderResult = FrameRenderResult.COMPLETE
        }

        object AllocationFreeBackend : AudioRenderBackend {
            private val properties = AudioBackendStreamProperties(48_000, 1, 4, 8)

            override fun open(
                request: AudioBackendOpenRequest,
                failureSink: AudioBackendFailureSink
            ): AudioBackendStreamProperties = properties

            override fun start(): Boolean = true
            override fun render(
                monoPcm: ShortArray,
                frameOffset: Int,
                frameCount: Int,
                startFrame: Long
            ): Int = frameCount

            override fun stop(): Boolean = true
            override fun timestamp(destination: AudioFrameTimestamp): Boolean = false
        }
    }
}
