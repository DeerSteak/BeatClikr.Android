package com.bfunkstudios.beatclikr.services

import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class AudioTrackFrameSessionSnapshot(
    val properties: AudioBackendStreamProperties?,
    val nextFrame: Long,
    val renderedBlocks: Long,
    val renderedBeatEvents: Long,
    val renderedRhythmEvents: Long,
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
    private var renderedBeatEvents = 0L

    @Volatile
    private var renderedRhythmEvents = 0L

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
                        owner.stop()
                        publishProperties(null)
                        return@post
                    }
                    val firstOutputFrame = owner.publicationFirstOutputFrame
                    if (!owner.start(firstOutputFrame)) {
                        owner.stop()
                        publishProperties(null)
                        return@post
                    }
                    if (cancelled.get()) {
                        owner.stop()
                        publishProperties(null)
                        return@post
                    }
                    snapshotSequence++
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
                owner.stop()
                publishProperties(null)
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
                    stopped = owner.stop()
                    publishProperties(null)
                } finally {
                    latch.countDown()
                }
            }
        ) {
            return false
        }
        return latch.await(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS) && stopped
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
        var nextFrame: Long
        var blocks: Long
        var beatEvents: Long
        var rhythmEvents: Long
        var recordedFailures: List<AudioBackendFailure>
        do {
            before = snapshotSequence
            properties = obtainedProperties
            nextFrame = writtenFrame
            blocks = renderedBlocks
            beatEvents = renderedBeatEvents
            rhythmEvents = renderedRhythmEvents
            recordedFailures = failureRing.snapshot()
            after = snapshotSequence
        } while (before != after || before and 1 != 0)
        return AudioTrackFrameSessionSnapshot(
            properties,
            nextFrame,
            blocks,
            beatEvents,
            rhythmEvents,
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
                renderedBlocks++
            }
            snapshotSequence++
            when (result) {
                FrameStreamRenderResult.COMPLETE -> handler.post(this)
                else -> {
                    renderRunning = false
                    owner.stop()
                    publishProperties(null)
                }
            }
        }
    }

    private fun publishProperties(properties: AudioBackendStreamProperties?) {
        snapshotSequence++
        obtainedProperties = properties
        snapshotSequence++
    }

    private fun recordFailure(failure: AudioBackendFailure) {
        snapshotSequence++
        failureRing.record(failure)
        snapshotSequence++
    }

    private fun resetSessionMetrics() {
        snapshotSequence++
        renderedBlocks = 0
        writtenFrame = 0
        renderedBeatEvents = 0
        renderedRhythmEvents = 0
        failureRing.reset()
        snapshotSequence++
    }

    private companion object {
        const val FAILURE_CAPACITY = 32
        const val START_TIMEOUT_SECONDS = 1L
        const val STOP_TIMEOUT_SECONDS = 1L
    }
}
