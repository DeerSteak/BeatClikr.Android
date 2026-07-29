package com.bfunkstudios.beatclikr.services

enum class AudioBackendOperation {
    OPEN,
    START,
    RENDER,
    STOP,
    TIMESTAMP
}

enum class AudioBackendFailureCode {
    INVALID_CONFIGURATION,
    STREAM_UNAVAILABLE,
    START_REJECTED,
    WRITE_FAILED,
    TIMESTAMP_UNAVAILABLE,
    DEVICE_DISCONNECTED,
    INTERNAL_ERROR
}

data class AudioBackendFailure(
    val operation: AudioBackendOperation,
    val code: AudioBackendFailureCode
)

fun interface AudioBackendFailureSink {
    fun report(failure: AudioBackendFailure)
}

data class AudioBackendOpenRequest(
    val preferredSampleRate: Int,
    val preferredChannelCount: Int,
    val preferredBufferFrames: Int
) {
    init {
        require(preferredSampleRate > 0) { "Preferred sample rate must be positive" }
        require(preferredChannelCount == 1) { "Phase 3 renderer requires mono output" }
        require(preferredBufferFrames > 0) { "Preferred buffer frames must be positive" }
    }
}

data class AudioBackendStreamProperties(
    val sampleRate: Int,
    val channelCount: Int,
    val burstFrames: Int,
    val bufferFrames: Int
) {
    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(channelCount > 0) { "Channel count must be positive" }
        require(burstFrames > 0) { "Burst frames must be positive" }
        require(bufferFrames >= burstFrames) { "Buffer must contain at least one burst" }
    }
}

class AudioFrameTimestamp(
    var framePosition: Long = 0,
    var monotonicTimeNanos: Long = 0
)

/** Backend-neutral PCM output; every unsuccessful operation reports through the failure sink. */
interface AudioRenderBackend {
    fun open(
        request: AudioBackendOpenRequest,
        failureSink: AudioBackendFailureSink
    ): AudioBackendStreamProperties?

    fun start(): Boolean

    fun render(
        interleavedPcm: ShortArray,
        frameOffset: Int,
        frameCount: Int,
        startFrame: Long
    ): Int

    fun stop(): Boolean

    fun timestamp(destination: AudioFrameTimestamp): Boolean
}
