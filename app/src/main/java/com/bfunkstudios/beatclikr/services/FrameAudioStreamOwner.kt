package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.DeadlineRecovery
import com.bfunkstudios.beatclikr.music.DeadlineRecoveryState
import com.bfunkstudios.beatclikr.music.FrameEventTimeline
import com.bfunkstudios.beatclikr.music.PolyrhythmConfiguration
import com.bfunkstudios.beatclikr.music.StandardMetronomeConfiguration

fun interface PcmFrameRendererFactory {
    fun create(properties: AudioBackendStreamProperties): PublishedPcmFrameRenderer?
}

class PublishedPcmFrameRenderer(
    val renderer: PcmFrameRenderer,
    val recovery: FrameStreamRecovery? = null,
    val firstOutputFrame: Long = 0,
    val firstEventFrame: Long = firstOutputFrame,
    val standardUpdater: StandardFrameStreamUpdater? = null,
    val polyrhythmUpdater: PolyrhythmFrameStreamUpdater? = null
)

fun interface StandardFrameStreamUpdater {
    fun update(
        configuration: StandardMetronomeConfiguration,
        firstUnprocessedFrame: Long
    ): FrameEventTimeline?
}

fun interface PolyrhythmFrameStreamUpdater {
    fun update(
        configuration: PolyrhythmConfiguration,
        firstUnprocessedFrame: Long
    ): FrameEventTimeline?
}

interface FrameStreamRecovery {
    fun start(firstOutputFrame: Long): Boolean
    fun recover(firstUnprocessedFrame: Long, nextRenderFrame: Long): Boolean
}

class TimelineFrameStreamRecovery(
    private var timeline: FrameEventTimeline
) : FrameStreamRecovery {
    private var state = DeadlineRecoveryState.atOrigin(timeline)

    val snapshot: DeadlineRecoveryState
        get() = state

    override fun start(firstOutputFrame: Long): Boolean =
        firstOutputFrame <= timeline.origin.originFrame

    override fun recover(
        firstUnprocessedFrame: Long,
        nextRenderFrame: Long
    ): Boolean {
        return try {
            val firstEventFrame = timeline.origin.originFrame
            val synchronized = state.synchronizedTo(
                maxOf(firstUnprocessedFrame, firstEventFrame)
            )
            state = DeadlineRecovery.recoverTo(
                timeline,
                synchronized,
                maxOf(nextRenderFrame, firstEventFrame)
            )
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ArithmeticException) {
            false
        }
    }

    fun replaceTimeline(replacement: FrameEventTimeline, firstUnprocessedFrame: Long): Boolean {
        return try {
            state = state.synchronizedTo(firstUnprocessedFrame)
            timeline = replacement
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

enum class FrameStreamRenderResult {
    COMPLETE,
    NOT_RUNNING,
    FRAME_RANGE_EXHAUSTED,
    RENDER_FAILED,
    WRITE_FAILED
}

class FrameAudioStreamOwner(
    private val backend: AudioRenderBackend
) {
    private var failureSink = AudioBackendFailureSink {}
    private var renderer: PcmFrameRenderer? = null
    private var recovery: FrameStreamRecovery? = null
    private var standardUpdater: StandardFrameStreamUpdater? = null
    private var polyrhythmUpdater: PolyrhythmFrameStreamUpdater? = null
    private var renderBuffer = ShortArray(0)
    private var backendStarted = false
    private var running = false

    var nextFrame: Long = 0
        private set

    var properties: AudioBackendStreamProperties? = null
        private set

    var publicationFirstOutputFrame: Long = 0
        private set
    var publicationFirstEventFrame: Long = 0
        private set

    val renderedBeatEvents: Long
        get() = renderer?.renderedBeatEvents ?: 0

    val renderedRhythmEvents: Long
        get() = renderer?.renderedRhythmEvents ?: 0

    val underrunCount: Int
        get() = backend.underrunCount()

    val route: AudioOutputRoute
        get() = backend.currentRoute()

    var lastMixDurationNanos: Long = 0L
        private set

    var lastWriteDurationNanos: Long = 0L
        private set

    val deadlineMisses: Long
        get() = (recovery as? TimelineFrameStreamRecovery)?.snapshot?.diagnostics?.deadlineMisses ?: 0L

    val droppedEvents: Long
        get() = (recovery as? TimelineFrameStreamRecovery)?.snapshot?.diagnostics?.droppedEvents ?: 0L

    fun open(
        request: AudioBackendOpenRequest,
        rendererFactory: PcmFrameRendererFactory,
        failureSink: AudioBackendFailureSink
    ): AudioBackendStreamProperties? {
        if (properties != null) {
            failureSink.report(
                AudioBackendFailure(
                    AudioBackendOperation.OPEN,
                    AudioBackendFailureCode.INVALID_CONFIGURATION
                )
            )
            return null
        }
        this.failureSink = failureSink
        val obtained = backend.open(request, failureSink) ?: return null
        var factoryReportedFailure = false
        val publication = try {
            rendererFactory.create(obtained)
        } catch (failure: RuntimeException) {
            factoryReportedFailure = true
            report(
                AudioBackendOperation.OPEN,
                if (failure is IllegalArgumentException) {
                    AudioBackendFailureCode.INVALID_CONFIGURATION
                } else {
                    AudioBackendFailureCode.INTERNAL_ERROR
                }
            )
            null
        }
        if (publication == null) {
            if (!factoryReportedFailure) {
                report(AudioBackendOperation.OPEN, AudioBackendFailureCode.INVALID_CONFIGURATION)
            }
            backend.stop()
            return null
        }
        val publishedRenderer = publication.renderer
        val blockFrames = obtained.burstFrames
        try {
            publishedRenderer.prepare(blockFrames)
        } catch (failure: RuntimeException) {
            report(
                AudioBackendOperation.OPEN,
                if (failure is IllegalArgumentException) {
                    AudioBackendFailureCode.INVALID_CONFIGURATION
                } else {
                    AudioBackendFailureCode.INTERNAL_ERROR
                }
            )
            backend.stop()
            return null
        }
        renderBuffer = ShortArray(blockFrames)
        renderer = publishedRenderer
        recovery = publication.recovery
        standardUpdater = publication.standardUpdater
        polyrhythmUpdater = publication.polyrhythmUpdater
        publicationFirstOutputFrame = publication.firstOutputFrame
        publicationFirstEventFrame = publication.firstEventFrame
        properties = obtained
        return obtained
    }

    fun start(firstOutputFrame: Long): Boolean {
        val publishedRenderer = renderer
        if (
            publishedRenderer == null ||
            firstOutputFrame < 0 ||
            backendStarted ||
            running
        ) {
            report(AudioBackendOperation.START, AudioBackendFailureCode.START_REJECTED)
            return false
        }
        if (recovery?.start(firstOutputFrame) == false) {
            report(AudioBackendOperation.START, AudioBackendFailureCode.INVALID_CONFIGURATION)
            return false
        }
        publishedRenderer.reset()
        nextFrame = firstOutputFrame
        if (!backend.start()) {
            publishedRenderer.reset()
            return false
        }
        properties = backend.streamProperties() ?: properties
        backendStarted = true
        running = true
        return true
    }

    fun resync(firstOutputFrame: Long): Boolean {
        val publishedRenderer = renderer
        if (publishedRenderer == null || !backendStarted || firstOutputFrame < 0) {
            report(AudioBackendOperation.RESYNC, AudioBackendFailureCode.INVALID_CONFIGURATION)
            return false
        }
        if (recovery?.recover(nextFrame, firstOutputFrame) == false) {
            report(AudioBackendOperation.RESYNC, AudioBackendFailureCode.INVALID_CONFIGURATION)
            return false
        }
        publishedRenderer.reset()
        nextFrame = firstOutputFrame
        running = true
        return true
    }

    fun setMuted(muted: Boolean) {
        renderer?.setMuted(muted)
    }

    fun timestamp(destination: AudioFrameTimestamp): Boolean =
        backend.timestamp(destination)

    fun updateStandard(configuration: StandardMetronomeConfiguration): Boolean {
        return updateTimeline { standardUpdater?.update(configuration, nextFrame) }
    }

    fun updatePolyrhythm(configuration: PolyrhythmConfiguration): Boolean {
        return updateTimeline { polyrhythmUpdater?.update(configuration, nextFrame) }
    }

    private inline fun updateTimeline(replacement: () -> FrameEventTimeline?): Boolean {
        return try {
            val frameRenderer = renderer as? FramePcmRenderer ?: return false
            val timelineRecovery = recovery as? TimelineFrameStreamRecovery ?: return false
            val publishedReplacement = replacement() ?: return false
            if (!timelineRecovery.replaceTimeline(publishedReplacement, nextFrame)) return false
            frameRenderer.replaceEventSource(publishedReplacement)
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ArithmeticException) {
            false
        }
    }

    fun renderNextBlock(): FrameStreamRenderResult {
        val publishedRenderer = renderer
        if (!running || publishedRenderer == null) {
            report(AudioBackendOperation.RENDER, AudioBackendFailureCode.INVALID_CONFIGURATION)
            return FrameStreamRenderResult.NOT_RUNNING
        }
        if (renderBuffer.size.toLong() > Long.MAX_VALUE - nextFrame) {
            publishedRenderer.reset()
            running = false
            report(AudioBackendOperation.RENDER, AudioBackendFailureCode.INVALID_CONFIGURATION)
            return FrameStreamRenderResult.FRAME_RANGE_EXHAUSTED
        }
        val mixStartedNanos = System.nanoTime()
        val renderResult = publishedRenderer.render(
            nextFrame,
            renderBuffer,
            renderBuffer.size
        )
        lastMixDurationNanos = System.nanoTime() - mixStartedNanos
        if (renderResult != FrameRenderResult.COMPLETE) {
            publishedRenderer.reset()
            running = false
            report(AudioBackendOperation.RENDER, AudioBackendFailureCode.INTERNAL_ERROR)
            return FrameStreamRenderResult.RENDER_FAILED
        }

        var writtenFrames = 0
        val writeStartedNanos = System.nanoTime()
        while (writtenFrames < renderBuffer.size) {
            val remainingFrames = renderBuffer.size - writtenFrames
            val written = backend.render(
                renderBuffer,
                writtenFrames,
                remainingFrames,
                nextFrame + writtenFrames
            )
            if (written <= 0 || written > remainingFrames) {
                nextFrame += writtenFrames
                publishedRenderer.reset()
                running = false
                report(AudioBackendOperation.RENDER, AudioBackendFailureCode.WRITE_FAILED)
                lastWriteDurationNanos = System.nanoTime() - writeStartedNanos
                return FrameStreamRenderResult.WRITE_FAILED
            }
            writtenFrames += written
        }
        lastWriteDurationNanos = System.nanoTime() - writeStartedNanos
        nextFrame += writtenFrames
        return FrameStreamRenderResult.COMPLETE
    }

    fun stop(): Boolean {
        if (renderer == null && properties == null) return true
        running = false
        renderer?.reset()
        val stopped = backend.stop()
        backendStarted = false
        renderer = null
        recovery = null
        standardUpdater = null
        polyrhythmUpdater = null
        publicationFirstOutputFrame = 0
        publicationFirstEventFrame = 0
        properties = null
        renderBuffer = ShortArray(0)
        return stopped
    }

    private fun report(operation: AudioBackendOperation, code: AudioBackendFailureCode) {
        failureSink.report(AudioBackendFailure(operation, code))
    }
}
