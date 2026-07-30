package com.bfunkstudios.beatclikr.services

import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import com.bfunkstudios.beatclikr.music.PolyrhythmConfiguration
import com.bfunkstudios.beatclikr.music.StandardMetronomeConfiguration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TIMESTAMP_NANOS_PER_SECOND = 1_000_000_000L

data class AudioTrackFrameSessionSnapshot(
    val properties: AudioBackendStreamProperties?,
    val firstOutputFrame: Long,
    val nextFrame: Long,
    val writtenFrames: Long,
    val renderedFrames: Long,
    val estimatedPresentedFrames: Long?,
    val renderedBlocks: Long,
    val renderedBeatEvents: Long,
    val renderedRhythmEvents: Long,
    val underrunCount: Int,
    val underrunSkippedFrames: Long,
    val deadlineMisses: Long,
    val droppedEvents: Long,
    val mixDuration: RenderDurationPercentiles,
    val writeDuration: RenderDurationPercentiles,
    val route: AudioOutputRoute,
    val routeChangeCount: Long,
    val frameCorrelation: AudioFrameCorrelation?,
    val failures: List<AudioBackendFailure>
)

data class AudioFrameCorrelation(
    val writtenFrame: Long,
    val presentedFrame: Long,
    val presentationNanoTime: Long
)

internal fun missingPresentationFrames(
    previousPresentedFrame: Long,
    previousPresentationNanoTime: Long,
    currentPresentedFrame: Long,
    currentPresentationNanoTime: Long,
    sampleRate: Int
): Long {
    require(sampleRate > 0) { "Sample rate must be positive" }
    val elapsedNanos = currentPresentationNanoTime - previousPresentationNanoTime
    val presentedFrames = currentPresentedFrame - previousPresentedFrame
    if (elapsedNanos <= 0 || presentedFrames < 0) return 0
    val elapsedFrames = Math.addExact(
        Math.multiplyExact(elapsedNanos / TIMESTAMP_NANOS_PER_SECOND, sampleRate.toLong()),
        (elapsedNanos % TIMESTAMP_NANOS_PER_SECOND) * sampleRate /
            TIMESTAMP_NANOS_PER_SECOND
    )
    return (elapsedFrames - presentedFrames).coerceAtLeast(0)
}

class AudioTrackFrameSession(
    audioManager: AudioManager?,
    private val preferredSampleRate: Int,
    preferredBurstFrames: Int
) {
    init {
        require(preferredSampleRate > 0) { "Preferred sample rate must be positive" }
        require(preferredBurstFrames > 0) { "Preferred burst frames must be positive" }
    }

    private val thread = HandlerThread("AudioTrackFrameRenderThread").also { it.start() }
    private val handler = Handler(thread.looper)
    private val owner = FrameAudioStreamOwner(AudioTrackRenderBackend(audioManager))
    private val preferredBufferFrames = Math.multiplyExact(preferredBurstFrames, 2)
    @Volatile
    private var renderRunning = false

    @Volatile
    private var released = false

    @Volatile
    private var obtainedProperties: AudioBackendStreamProperties? = null

    @Volatile
    private var renderedBlocks = 0L

    @Volatile
    private var writtenFrame = 0L

    @Volatile
    private var writtenFrames = 0L

    @Volatile
    private var renderedFrames = 0L

    @Volatile
    private var firstOutputFrame = 0L

    @Volatile
    private var renderedBeatEvents = 0L

    @Volatile
    private var renderedRhythmEvents = 0L

    @Volatile
    private var underrunCount = 0

    @Volatile
    private var underrunSkippedFrames = 0L

    @Volatile
    private var deadlineMisses = 0L

    @Volatile
    private var droppedEvents = 0L

    private val mixDurations = RenderDurationHistogram()
    private val writeDurations = RenderDurationHistogram()

    @Volatile
    private var currentRoute = AudioOutputRoute.UNKNOWN

    @Volatile
    private var routeChangeCount = 0L

    private val timestamp = AudioFrameTimestamp()

    @Volatile
    private var hasFrameCorrelation = false

    @Volatile
    private var correlatedWrittenFrame = 0L

    @Volatile
    private var presentedFrame = 0L

    @Volatile
    private var presentationNanoTime = 0L

    private val failureRing = AudioBackendFailureRing(FAILURE_CAPACITY)

    @Volatile
    private var snapshotSequence = 0

    fun start(rendererFactory: PcmFrameRendererFactory): Boolean {
        if (released) return false
        val latch = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        var started = false
        if (!handler.post {
                try {
                    if (renderRunning) {
                        recordFailure(
                            AudioBackendFailure(
                                AudioBackendOperation.START,
                                AudioBackendFailureCode.START_REJECTED
                            )
                        )
                        return@post
                    }
                    resetSessionMetrics()
                    val properties = owner.open(
                        AudioBackendOpenRequest(
                            preferredSampleRate,
                            preferredChannelCount = 1,
                            preferredBufferFrames = preferredBufferFrames
                        ),
                        rendererFactory,
                        ::recordFailure
                    ) ?: return@post
                    publishProperties(properties)
                    if (cancelled.get()) {
                        captureUnderruns()
                        owner.stop()
                        return@post
                    }
                    val firstOutputFrame = owner.publicationFirstOutputFrame
                    if (!owner.start(firstOutputFrame)) {
                        captureUnderruns()
                        owner.stop()
                        return@post
                    }
                    if (cancelled.get()) {
                        captureUnderruns()
                        owner.stop()
                        return@post
                    }
                    snapshotSequence++
                    this.firstOutputFrame = firstOutputFrame
                    writtenFrame = firstOutputFrame
                    currentRoute = owner.route
                    snapshotSequence++
                    renderRunning = true
                    started = true
                    handler.post(renderRunnable)
                } finally {
                    latch.countDown()
                }
            }
        ) {
            return false
        }
        if (!latch.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            cancelled.set(true)
            handler.post {
                renderRunning = false
                handler.removeCallbacks(renderRunnable)
                captureUnderruns()
                owner.stop()
            }
            return false
        }
        return started
    }

    fun stop(): Boolean {
        if (released) return true
        val latch = CountDownLatch(1)
        var stopped = true
        if (!handler.post {
                try {
                    renderRunning = false
                    handler.removeCallbacks(renderRunnable)
                    captureUnderruns()
                    stopped = owner.stop()
                } finally {
                    latch.countDown()
                }
            }
        ) {
            return false
        }
        return latch.await(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS) && stopped
    }

    fun setMuted(muted: Boolean) {
        if (!released) handler.post { owner.setMuted(muted) }
    }

    fun updateStandard(configuration: StandardMetronomeConfiguration): Boolean {
        if (released || !renderRunning) return false
        return handler.post {
            if (renderRunning && !owner.updateStandard(configuration)) {
                recordFailure(
                    AudioBackendFailure(
                        AudioBackendOperation.RENDER,
                        AudioBackendFailureCode.INVALID_CONFIGURATION
                    )
                )
            }
        }
    }

    fun updatePolyrhythm(configuration: PolyrhythmConfiguration): Boolean {
        if (released || !renderRunning) return false
        return handler.post {
            if (renderRunning && !owner.updatePolyrhythm(configuration)) {
                recordFailure(
                    AudioBackendFailure(
                        AudioBackendOperation.RENDER,
                        AudioBackendFailureCode.INVALID_CONFIGURATION
                    )
                )
            }
        }
    }

    @Synchronized
    fun release(): Boolean {
        if (released) return true
        val stopped = stop()
        released = true
        thread.quitSafely()
        return stopped
    }

    fun snapshot(): AudioTrackFrameSessionSnapshot {
        var before: Int
        var after: Int
        var properties: AudioBackendStreamProperties?
        var firstFrame: Long
        var nextFrame: Long
        var completedWrittenFrames: Long
        var completedRenderedFrames: Long
        var blocks: Long
        var beatEvents: Long
        var rhythmEvents: Long
        var underruns: Int
        var skippedFrames: Long
        var missedDeadlines: Long
        var expiredEvents: Long
        var mixDurationPercentiles: RenderDurationPercentiles
        var writeDurationPercentiles: RenderDurationPercentiles
        var route: AudioOutputRoute
        var routeChanges: Long
        var hasCorrelation: Boolean
        var correlationWrittenFrame: Long
        var correlationPresentedFrame: Long
        var correlationNanoTime: Long
        var recordedFailures: List<AudioBackendFailure>
        do {
            before = snapshotSequence
            properties = obtainedProperties
            firstFrame = firstOutputFrame
            nextFrame = writtenFrame
            completedWrittenFrames = writtenFrames
            completedRenderedFrames = renderedFrames
            blocks = renderedBlocks
            beatEvents = renderedBeatEvents
            rhythmEvents = renderedRhythmEvents
            underruns = underrunCount
            skippedFrames = underrunSkippedFrames
            missedDeadlines = deadlineMisses
            expiredEvents = droppedEvents
            mixDurationPercentiles = mixDurations.percentiles()
            writeDurationPercentiles = writeDurations.percentiles()
            route = currentRoute
            routeChanges = routeChangeCount
            hasCorrelation = hasFrameCorrelation
            correlationWrittenFrame = correlatedWrittenFrame
            correlationPresentedFrame = presentedFrame
            correlationNanoTime = presentationNanoTime
            recordedFailures = failureRing.snapshot()
            after = snapshotSequence
        } while (before != after || before and 1 != 0)
        return AudioTrackFrameSessionSnapshot(
            properties,
            firstFrame,
            nextFrame,
            completedWrittenFrames,
            completedRenderedFrames,
            if (hasCorrelation) correlationPresentedFrame else null,
            blocks,
            beatEvents,
            rhythmEvents,
            underruns,
            skippedFrames,
            missedDeadlines,
            expiredEvents,
            mixDurationPercentiles,
            writeDurationPercentiles,
            route,
            routeChanges,
            if (hasCorrelation) {
                AudioFrameCorrelation(
                    correlationWrittenFrame,
                    correlationPresentedFrame,
                    correlationNanoTime
                )
            } else {
                null
            },
            recordedFailures
        )
    }

    private val renderRunnable = object : Runnable {
        override fun run() {
            if (!renderRunning) return
            val result = owner.renderNextBlock()
            var canContinue = result == FrameStreamRenderResult.COMPLETE
            snapshotSequence++
            mixDurations.record(owner.lastMixDurationNanos)
            writeDurations.record(owner.lastWriteDurationNanos)
            val observedRoute = owner.route
            if (observedRoute != currentRoute) {
                if (currentRoute != AudioOutputRoute.UNKNOWN) routeChangeCount++
                currentRoute = observedRoute
            }
            writtenFrame = owner.nextFrame
            if (result == FrameStreamRenderResult.COMPLETE) {
                val properties = owner.properties
                if (properties == null) {
                    failureRing.record(
                        AudioBackendFailure(
                            AudioBackendOperation.RENDER,
                            AudioBackendFailureCode.INTERNAL_ERROR
                        )
                    )
                    canContinue = false
                } else {
                    renderedBeatEvents = owner.renderedBeatEvents
                    renderedRhythmEvents = owner.renderedRhythmEvents
                    deadlineMisses = owner.deadlineMisses
                    droppedEvents = owner.droppedEvents
                    val observedUnderruns = owner.underrunCount
                    val missingFrames = captureFrameCorrelation(properties.sampleRate)
                    if (observedUnderruns > underrunCount) {
                        val recoveryFrame = Math.addExact(owner.nextFrame, missingFrames)
                        canContinue = owner.resync(recoveryFrame)
                        if (canContinue) {
                            underrunSkippedFrames = Math.addExact(
                                underrunSkippedFrames,
                                missingFrames
                            )
                        }
                    }
                    underrunCount = observedUnderruns
                    writtenFrames = Math.addExact(
                        writtenFrames,
                        properties.burstFrames.toLong()
                    )
                    renderedFrames = Math.addExact(
                        renderedFrames,
                        properties.burstFrames.toLong()
                    )
                    renderedBlocks++
                }
            }
            snapshotSequence++
            if (canContinue) {
                handler.post(this)
            } else {
                renderRunning = false
                captureUnderruns()
                owner.stop()
            }
        }
    }

    private fun publishProperties(properties: AudioBackendStreamProperties?) {
        snapshotSequence++
        obtainedProperties = properties
        snapshotSequence++
    }

    private fun captureUnderruns() {
        snapshotSequence++
        underrunCount = owner.underrunCount
        snapshotSequence++
    }

    private fun captureFrameCorrelation(sampleRate: Int): Long {
        if (!owner.timestamp(timestamp)) return 0
        val missingFrames = if (hasFrameCorrelation) {
            missingPresentationFrames(
                presentedFrame,
                presentationNanoTime,
                timestamp.framePosition,
                timestamp.monotonicTimeNanos,
                sampleRate
            )
        } else {
            0
        }
        correlatedWrittenFrame = owner.nextFrame
        presentedFrame = timestamp.framePosition
        presentationNanoTime = timestamp.monotonicTimeNanos
        hasFrameCorrelation = true
        return missingFrames
    }

    private fun recordFailure(failure: AudioBackendFailure) {
        snapshotSequence++
        failureRing.record(failure)
        snapshotSequence++
    }

    private fun resetSessionMetrics() {
        snapshotSequence++
        obtainedProperties = null
        renderedBlocks = 0
        firstOutputFrame = 0
        writtenFrame = 0
        writtenFrames = 0
        renderedFrames = 0
        renderedBeatEvents = 0
        renderedRhythmEvents = 0
        underrunCount = 0
        underrunSkippedFrames = 0
        deadlineMisses = 0
        droppedEvents = 0
        mixDurations.reset()
        writeDurations.reset()
        currentRoute = AudioOutputRoute.UNKNOWN
        routeChangeCount = 0
        hasFrameCorrelation = false
        correlatedWrittenFrame = 0
        presentedFrame = 0
        presentationNanoTime = 0
        failureRing.reset()
        snapshotSequence++
    }

    private companion object {
        const val FAILURE_CAPACITY = 32
        const val START_TIMEOUT_SECONDS = 1L
        const val STOP_TIMEOUT_SECONDS = 1L
    }
}
