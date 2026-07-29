package com.bfunkstudios.beatclikr.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRenderBackendTest {
    @Test
    fun contractCarriesLifecycleRenderRangeAndTimestamp() {
        val backend = RecordingBackend()
        val failures = mutableListOf<AudioBackendFailure>()
        val request = AudioBackendOpenRequest(
            preferredSampleRate = 48_000,
            preferredChannelCount = 1,
            preferredBufferFrames = 384
        )

        val properties = requireNotNull(backend.open(request, failures::add))
        assertEquals(48_000, properties.sampleRate)
        assertEquals(192, properties.burstFrames)
        assertTrue(backend.start())

        val pcm = ShortArray(384)
        assertEquals(96, backend.render(pcm, frameOffset = 48, frameCount = 96, startFrame = 12_000))
        assertSame(pcm, backend.lastPcm)
        assertEquals(48, backend.lastFrameOffset)
        assertEquals(96, backend.lastFrameCount)
        assertEquals(12_000, backend.lastStartFrame)

        val timestamp = AudioFrameTimestamp()
        assertTrue(backend.timestamp(timestamp))
        assertEquals(12_096, timestamp.framePosition)
        assertEquals(900_000, timestamp.monotonicTimeNanos)
        assertTrue(backend.stop())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun backendReportsTypedFailuresThroughRegisteredSink() {
        val backend = RecordingBackend()
        val failures = mutableListOf<AudioBackendFailure>()
        backend.open(
            AudioBackendOpenRequest(48_000, 1, 384),
            failures::add
        )

        backend.fail(AudioBackendOperation.RENDER, AudioBackendFailureCode.DEVICE_DISCONNECTED)

        assertEquals(
            AudioBackendFailure(
                operation = AudioBackendOperation.RENDER,
                code = AudioBackendFailureCode.DEVICE_DISCONNECTED
            ),
            failures.single()
        )
        assertFalse(backend.timestamp(AudioFrameTimestamp()))
        assertEquals(AudioBackendFailureCode.TIMESTAMP_UNAVAILABLE, failures.last().code)
    }

    @Test
    fun unsuccessfulOpenAndBooleanOperationsReportTypedFailures() {
        val backend = RecordingBackend()
        val failures = mutableListOf<AudioBackendFailure>()
        backend.openSucceeds = false

        assertEquals(
            null,
            backend.open(AudioBackendOpenRequest(48_000, 1, 384), failures::add)
        )
        assertEquals(AudioBackendFailureCode.STREAM_UNAVAILABLE, failures.single().code)

        backend.openSucceeds = true
        backend.open(AudioBackendOpenRequest(48_000, 1, 384), failures::add)
        backend.startSucceeds = false
        assertFalse(backend.start())
        assertEquals(AudioBackendFailureCode.START_REJECTED, failures.last().code)
    }

    private class RecordingBackend : AudioRenderBackend {
        private var failureSink = AudioBackendFailureSink {}
        private var timestampAvailable = true
        var openSucceeds = true
        var startSucceeds = true
        var lastPcm: ShortArray? = null
        var lastFrameOffset = -1
        var lastFrameCount = -1
        var lastStartFrame = -1L

        override fun open(
            request: AudioBackendOpenRequest,
            failureSink: AudioBackendFailureSink
        ): AudioBackendStreamProperties? {
            this.failureSink = failureSink
            if (!openSucceeds) {
                fail(AudioBackendOperation.OPEN, AudioBackendFailureCode.STREAM_UNAVAILABLE)
                return null
            }
            return AudioBackendStreamProperties(
                sampleRate = request.preferredSampleRate,
                channelCount = request.preferredChannelCount,
                burstFrames = 192,
                bufferFrames = request.preferredBufferFrames
            )
        }

        override fun start(): Boolean {
            if (!startSucceeds) {
                fail(AudioBackendOperation.START, AudioBackendFailureCode.START_REJECTED)
                return false
            }
            return true
        }

        override fun render(
            interleavedPcm: ShortArray,
            frameOffset: Int,
            frameCount: Int,
            startFrame: Long
        ): Int {
            lastPcm = interleavedPcm
            lastFrameOffset = frameOffset
            lastFrameCount = frameCount
            lastStartFrame = startFrame
            return frameCount
        }

        override fun stop(): Boolean = true

        override fun timestamp(destination: AudioFrameTimestamp): Boolean {
            if (!timestampAvailable) {
                failureSink.report(
                    AudioBackendFailure(
                        AudioBackendOperation.TIMESTAMP,
                        AudioBackendFailureCode.TIMESTAMP_UNAVAILABLE
                    )
                )
                return false
            }
            destination.framePosition = 12_096
            destination.monotonicTimeNanos = 900_000
            return true
        }

        fun fail(operation: AudioBackendOperation, code: AudioBackendFailureCode) {
            timestampAvailable = false
            failureSink.report(AudioBackendFailure(operation, code))
        }
    }
}
