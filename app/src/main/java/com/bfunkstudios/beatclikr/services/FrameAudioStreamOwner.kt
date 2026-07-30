package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.DeadlineRecovery
import com.bfunkstudios.beatclikr.music.DeadlineRecoveryState
import com.bfunkstudios.beatclikr.music.FrameEventTimeline

fun interface PcmFrameRendererFactory {
    fun create(properties: AudioBackendStreamProperties): PublishedPcmFrameRenderer?
}

class PublishedPcmFrameRenderer(
    val renderer: PcmFrameRenderer,
    val recovery: FrameStreamRecovery? = null,
    val firstOutputFrame: Long = 0
)

interface FrameStreamRecovery {
    fun start(firstOutputFrame: Long): Boolean
    fun recover(firstUnprocessedFrame: Long, nextRenderFrame: Long): Boolean
}

class TimelineFrameStreamRecovery(
    private val timeline: FrameEventTimeline
) : FrameStreamRecovery {
    private var state = DeadlineRecoveryState.atOrigin(timeline)

    val snapshot: DeadlineRecoveryState
        get() = state

    override fun start(firstOutputFrame: Long): Boolean =
        firstOutputFrame == timeline.origin.originFrame

    override fun recover(
        firstUnprocessedFrame: Long,
        nextRenderFrame: Long
    ): Boolean {
        return try {
            val synchronized = state.synchronizedTo(firstUnprocessedFrame)
            state = DeadlineRecovery.recoverTo(timeline, synchronized, nextRenderFrame)
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ArithmeticException) {
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
    private var renderBuffer = ShortArray(0)
    private var backendStarted = false
    private var running = false

    var nextFrame: Long = 0
        private set

    var properties: AudioBackendStreamProperties? = null
        private set

    var publicationFirstOutputFrame: Long = 0
        private set

    val renderedBeatEvents: Long
        get() = renderer?.renderedBeatEvents ?: 0

    val renderedRhythmEvents: Long
        get() = renderer?.renderedRhythmEvents ?: 0

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
        publicationFirstOutputFrame = publication.firstOutputFrame
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
        val renderResult = publishedRenderer.render(
            nextFrame,
            renderBuffer,
            renderBuffer.size
        )
        if (renderResult != FrameRenderResult.COMPLETE) {
            publishedRenderer.reset()
            running = false
            report(AudioBackendOperation.RENDER, AudioBackendFailureCode.INTERNAL_ERROR)
            return FrameStreamRenderResult.RENDER_FAILED
        }

        var writtenFrames = 0
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
                return FrameStreamRenderResult.WRITE_FAILED
            }
            writtenFrames += written
        }
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
        publicationFirstOutputFrame = 0
        properties = null
        renderBuffer = ShortArray(0)
        return stopped
    }

    private fun report(operation: AudioBackendOperation, code: AudioBackendFailureCode) {
        failureSink.report(AudioBackendFailure(operation, code))
    }
}
