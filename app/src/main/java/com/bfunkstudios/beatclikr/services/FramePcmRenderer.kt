package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.FrameRangeEventConsumer
import com.bfunkstudios.beatclikr.music.FrameRangeEventSource
import com.bfunkstudios.beatclikr.music.SoundRole

/** Stable waveform references prepared and published before rendering begins. */
class RenderWaveforms(
    val beat: ShortArray,
    val rhythm: ShortArray
) {
    init {
        require(beat.isNotEmpty()) { "Beat waveform must not be empty" }
        require(rhythm.isNotEmpty()) { "Rhythm waveform must not be empty" }
    }
}

enum class FrameRenderResult {
    COMPLETE,
    EVENT_SOURCE_FAILED,
    VOICE_CAPACITY_EXCEEDED,
    INVALID_RANGE
}

interface PcmFrameRenderer {
    fun prepare(maximumBlockFrames: Int)
    fun reset()
    fun render(startFrame: Long, output: ShortArray, frameCount: Int): FrameRenderResult
}

class FramePcmRenderer(
    private val eventSource: FrameRangeEventSource,
    private val waveforms: RenderWaveforms,
    maximumActiveVoices: Int
) : FrameRangeEventConsumer, PcmFrameRenderer {
    private val activeRoles = ByteArray(maximumActiveVoices)
    private val activePositions = IntArray(maximumActiveVoices)
    private var accumulator = IntArray(0)
    private var blockStartFrame = 0L
    private var blockFrameCount = 0
    private var result = FrameRenderResult.COMPLETE
    private var hasExpectedFrame = false
    private var nextExpectedFrame = 0L

    override fun accept(
        intendedFrame: Long,
        primarySound: SoundRole,
        secondarySound: SoundRole?,
        muted: Boolean
    ): Boolean {
        if (!muted) {
            addVoice(primarySound, intendedFrame)
            if (result == FrameRenderResult.COMPLETE && secondarySound != null) {
                addVoice(secondarySound, intendedFrame)
            }
        }
        return result == FrameRenderResult.COMPLETE
    }

    init {
        require(maximumActiveVoices > 0) { "Maximum active voices must be positive" }
    }

    override fun prepare(maximumBlockFrames: Int) {
        require(maximumBlockFrames > 0) { "Maximum block frames must be positive" }
        if (accumulator.size < maximumBlockFrames) {
            accumulator = IntArray(maximumBlockFrames)
        }
    }

    override fun reset() {
        var slot = 0
        while (slot < activeRoles.size) {
            activeRoles[slot] = NO_VOICE
            activePositions[slot] = 0
            slot++
        }
        hasExpectedFrame = false
        nextExpectedFrame = 0
    }

    override fun render(startFrame: Long, output: ShortArray, frameCount: Int): FrameRenderResult {
        if (
            startFrame < 0 ||
            frameCount < 0 ||
            frameCount > output.size ||
            frameCount > accumulator.size ||
            frameCount.toLong() > Long.MAX_VALUE - startFrame ||
            hasExpectedFrame && startFrame != nextExpectedFrame
        ) {
            return FrameRenderResult.INVALID_RANGE
        }
        val endFrame = startFrame + frameCount
        blockStartFrame = startFrame
        blockFrameCount = frameCount
        result = FrameRenderResult.COMPLETE
        accumulator.fill(0, 0, frameCount)

        mixExistingVoices()
        if (!eventSource.visitEvents(startFrame, endFrame, this)) {
            result = FrameRenderResult.EVENT_SOURCE_FAILED
        }
        if (result != FrameRenderResult.COMPLETE) {
            output.fill(0, 0, frameCount)
            reset()
            return result
        }
        writeSaturatedOutput(output, frameCount)
        hasExpectedFrame = true
        nextExpectedFrame = endFrame
        return result
    }

    private fun addVoice(role: SoundRole, intendedFrame: Long) {
        val offset = intendedFrame - blockStartFrame
        if (offset < 0 || offset >= blockFrameCount) {
            result = FrameRenderResult.EVENT_SOURCE_FAILED
            return
        }
        var slot = 0
        while (slot < activeRoles.size && activeRoles[slot] != NO_VOICE) slot++
        if (slot == activeRoles.size) {
            result = FrameRenderResult.VOICE_CAPACITY_EXCEEDED
            return
        }
        activeRoles[slot] = if (role == SoundRole.BEAT) BEAT_VOICE else RHYTHM_VOICE
        activePositions[slot] = 0
        mixVoice(slot, offset.toInt())
    }

    private fun mixExistingVoices() {
        var slot = 0
        while (slot < activeRoles.size) {
            if (activeRoles[slot] != NO_VOICE) mixVoice(slot, 0)
            slot++
        }
    }

    private fun mixVoice(slot: Int, outputOffset: Int) {
        val waveform = when (activeRoles[slot]) {
            BEAT_VOICE -> waveforms.beat
            RHYTHM_VOICE -> waveforms.rhythm
            else -> return
        }
        var sourceIndex = activePositions[slot]
        var outputIndex = outputOffset
        while (sourceIndex < waveform.size && outputIndex < blockFrameCount) {
            accumulator[outputIndex] += waveform[sourceIndex].toInt()
            sourceIndex++
            outputIndex++
        }
        if (sourceIndex == waveform.size) {
            activeRoles[slot] = NO_VOICE
            activePositions[slot] = 0
        } else {
            activePositions[slot] = sourceIndex
        }
    }

    private fun writeSaturatedOutput(output: ShortArray, frameCount: Int) {
        var index = 0
        while (index < frameCount) {
            output[index] = accumulator[index]
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            index++
        }
    }

    private companion object {
        const val NO_VOICE: Byte = 0
        const val BEAT_VOICE: Byte = 1
        const val RHYTHM_VOICE: Byte = 2
    }
}
