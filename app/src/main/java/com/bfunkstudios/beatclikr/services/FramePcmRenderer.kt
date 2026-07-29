package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.SoundRole

fun interface RenderEventConsumer {
    fun accept(
        intendedFrame: Long,
        primarySound: SoundRole,
        secondarySound: SoundRole?,
        muted: Boolean
    ): Boolean
}

/** Visits a range without allocation, locks, I/O, logging, or thread handoff. */
interface RenderEventSource {
    fun visitEvents(
        startFrame: Long,
        endFrameExclusive: Long,
        consumer: RenderEventConsumer
    ): Boolean
}

/** Stable waveform references prepared and published before rendering begins. */
class RenderWaveforms(
    val beat: ShortArray,
    val rhythm: ShortArray
) {
    init {
        require(beat.isNotEmpty()) { "Beat waveform must not be empty" }
        require(rhythm.isNotEmpty()) { "Rhythm waveform must not be empty" }
    }

    fun waveform(role: SoundRole): ShortArray =
        when (role) {
            SoundRole.BEAT -> beat
            SoundRole.RHYTHM -> rhythm
        }
}

enum class FrameRenderResult {
    COMPLETE,
    EVENT_SOURCE_FAILED,
    VOICE_CAPACITY_EXCEEDED,
    INVALID_RANGE
}

class FramePcmRenderer(
    private val eventSource: RenderEventSource,
    private val waveforms: RenderWaveforms,
    maximumActiveVoices: Int
) {
    private val activeWaveforms: Array<ShortArray?> = arrayOfNulls(maximumActiveVoices)
    private val activePositions = IntArray(maximumActiveVoices)
    private var accumulator = IntArray(0)
    private var blockStartFrame = 0L
    private var blockFrameCount = 0
    private var result = FrameRenderResult.COMPLETE

    private val eventConsumer = RenderEventConsumer { intendedFrame, primary, secondary, muted ->
        if (!muted) {
            addVoice(primary, intendedFrame)
            if (result == FrameRenderResult.COMPLETE && secondary != null) {
                addVoice(secondary, intendedFrame)
            }
        }
        result == FrameRenderResult.COMPLETE
    }

    init {
        require(maximumActiveVoices > 0) { "Maximum active voices must be positive" }
    }

    fun prepare(maximumBlockFrames: Int) {
        require(maximumBlockFrames > 0) { "Maximum block frames must be positive" }
        if (accumulator.size < maximumBlockFrames) {
            accumulator = IntArray(maximumBlockFrames)
        }
    }

    fun render(startFrame: Long, output: ShortArray, frameCount: Int): FrameRenderResult {
        if (startFrame < 0 || frameCount < 0 || frameCount > output.size || frameCount > accumulator.size) {
            return FrameRenderResult.INVALID_RANGE
        }
        blockStartFrame = startFrame
        blockFrameCount = frameCount
        result = FrameRenderResult.COMPLETE
        accumulator.fill(0, 0, frameCount)

        mixExistingVoices()
        val endFrame = startFrame + frameCount
        if (endFrame < startFrame) return FrameRenderResult.INVALID_RANGE
        if (!eventSource.visitEvents(startFrame, endFrame, eventConsumer)) {
            result = FrameRenderResult.EVENT_SOURCE_FAILED
        }
        writeSaturatedOutput(output, frameCount)
        return result
    }

    private fun addVoice(role: SoundRole, intendedFrame: Long) {
        val offset = intendedFrame - blockStartFrame
        if (offset < 0 || offset >= blockFrameCount) {
            result = FrameRenderResult.EVENT_SOURCE_FAILED
            return
        }
        var slot = 0
        while (slot < activeWaveforms.size && activeWaveforms[slot] != null) slot++
        if (slot == activeWaveforms.size) {
            result = FrameRenderResult.VOICE_CAPACITY_EXCEEDED
            return
        }
        activeWaveforms[slot] = waveforms.waveform(role)
        activePositions[slot] = 0
        mixVoice(slot, offset.toInt())
    }

    private fun mixExistingVoices() {
        var slot = 0
        while (slot < activeWaveforms.size) {
            if (activeWaveforms[slot] != null) mixVoice(slot, 0)
            slot++
        }
    }

    private fun mixVoice(slot: Int, outputOffset: Int) {
        val waveform = activeWaveforms[slot] ?: return
        var sourceIndex = activePositions[slot]
        var outputIndex = outputOffset
        while (sourceIndex < waveform.size && outputIndex < blockFrameCount) {
            accumulator[outputIndex] += waveform[sourceIndex].toInt()
            sourceIndex++
            outputIndex++
        }
        if (sourceIndex == waveform.size) {
            activeWaveforms[slot] = null
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
}
