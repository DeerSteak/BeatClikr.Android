package com.bfunkstudios.beatclikr.services

enum class AudioBackendOperation {
    OPEN,
    START,
    RENDER,
    RESYNC,
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

enum class AudioBackendPerformanceMode {
    NONE,
    LOW_LATENCY,
    POWER_SAVING,
    UNKNOWN
}

data class AudioBackendFailure(
    val operation: AudioBackendOperation,
    val code: AudioBackendFailureCode
)

class AudioBackendFailureRing(capacity: Int) {
    init {
        require(capacity > 0) { "Failure capacity must be positive" }
    }

    private val storage = arrayOfNulls<AudioBackendFailure>(capacity)
    private var count = 0
    private var nextIndex = 0

    fun record(failure: AudioBackendFailure) {
        storage[nextIndex] = failure
        nextIndex = (nextIndex + 1) % storage.size
        if (count < storage.size) count++
    }

    fun reset() {
        storage.fill(null)
        count = 0
        nextIndex = 0
    }

    fun snapshot(): List<AudioBackendFailure> {
        if (count == 0) return emptyList()
        val copy = ArrayList<AudioBackendFailure>(count)
        val oldest = if (count == storage.size) nextIndex else 0
        var offset = 0
        while (offset < count) {
            storage[(oldest + offset) % storage.size]?.let(copy::add)
            offset++
        }
        return copy
    }
}

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
    val bufferFrames: Int,
    val performanceMode: AudioBackendPerformanceMode = AudioBackendPerformanceMode.UNKNOWN
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

/** Backend-neutral mono PCM output; the backend expands channels and reports unsuccessful operations. */
interface AudioRenderBackend {
    fun open(
        request: AudioBackendOpenRequest,
        failureSink: AudioBackendFailureSink
    ): AudioBackendStreamProperties?

    fun start(): Boolean

    /** Offsets, counts, start positions, and results are renderer frames, never interleaved samples. */
    fun render(
        monoPcm: ShortArray,
        frameOffset: Int,
        frameCount: Int,
        startFrame: Long
    ): Int

    fun stop(): Boolean

    fun timestamp(destination: AudioFrameTimestamp): Boolean

    fun underrunCount(): Int = 0
}

/** Expands mono renderer frames into an obtained interleaved output layout. */
class MonoPcmChannelAdapter(maximumFrames: Int, val channelCount: Int) {
    init {
        require(maximumFrames > 0) { "Maximum frames must be positive" }
        require(channelCount > 0) { "Channel count must be positive" }
    }

    private val interleaved = ShortArray(
        Math.multiplyExact(maximumFrames, channelCount)
    )

    fun adapt(
        monoPcm: ShortArray,
        frameOffset: Int,
        frameCount: Int
    ): ShortArray? {
        if (
            frameOffset < 0 ||
            frameCount < 0 ||
            frameOffset > monoPcm.size - frameCount ||
            frameCount > interleaved.size / channelCount
        ) {
            return null
        }
        var frame = 0
        var outputIndex = 0
        while (frame < frameCount) {
            val sample = monoPcm[frameOffset + frame]
            var channel = 0
            while (channel < channelCount) {
                interleaved[outputIndex] = sample
                outputIndex++
                channel++
            }
            frame++
        }
        return interleaved
    }
}
