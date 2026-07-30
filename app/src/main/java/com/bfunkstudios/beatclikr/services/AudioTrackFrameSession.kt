package com.bfunkstudios.beatclikr.services

import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import com.bfunkstudios.beatclikr.music.PolyrhythmConfiguration
import com.bfunkstudios.beatclikr.music.StandardMetronomeConfiguration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class AudioTrackFrameSessionSnapshot(
    val properties: AudioBackendStreamProperties?,
    val firstOutputFrame: Long,
    val nextFrame: Long,
    val renderedBlocks: Long,
    val renderedBeatEvents: Long,
    val renderedRhythmEvents: Long,
    val underrunCount: Int,
    val failures: List<AudioBackendFailure>
)

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
    private var firstOutputFrame = 0L

    @Volatile
    private var renderedBeatEvents = 0L

    @Volatile
    private var renderedRhythmEvents = 0L

    @Volatile
    private var underrunCount = 0

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
        var blocks: Long
        var beatEvents: Long
        var rhythmEvents: Long
        var underruns: Int
        var recordedFailures: List<AudioBackendFailure>
        do {
            before = snapshotSequence
            properties = obtainedProperties
            firstFrame = firstOutputFrame
            nextFrame = writtenFrame
            blocks = renderedBlocks
            beatEvents = renderedBeatEvents
            rhythmEvents = renderedRhythmEvents
            underruns = underrunCount
            recordedFailures = failureRing.snapshot()
            after = snapshotSequence
        } while (before != after || before and 1 != 0)
        return AudioTrackFrameSessionSnapshot(
            properties,
            firstFrame,
            nextFrame,
            blocks,
            beatEvents,
            rhythmEvents,
            underruns,
            recordedFailures
        )
    }

    private val renderRunnable = object : Runnable {
        override fun run() {
            if (!renderRunning) return
            val result = owner.renderNextBlock()
            snapshotSequence++
            writtenFrame = owner.nextFrame
            if (result == FrameStreamRenderResult.COMPLETE) {
                renderedBeatEvents = owner.renderedBeatEvents
                renderedRhythmEvents = owner.renderedRhythmEvents
                underrunCount = owner.underrunCount
                renderedBlocks++
            }
            snapshotSequence++
            when (result) {
                FrameStreamRenderResult.COMPLETE -> handler.post(this)
                else -> {
                    renderRunning = false
                    captureUnderruns()
                    owner.stop()
                }
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
        renderedBeatEvents = 0
        renderedRhythmEvents = 0
        underrunCount = 0
        failureRing.reset()
        snapshotSequence++
    }

    private companion object {
        const val FAILURE_CAPACITY = 32
        const val START_TIMEOUT_SECONDS = 1L
        const val STOP_TIMEOUT_SECONDS = 1L
    }
}
