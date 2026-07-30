package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.FrameRangeEventConsumer
import com.bfunkstudios.beatclikr.music.FrameRangeEventSource
import com.bfunkstudios.beatclikr.music.MusicalEventRole
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
    val renderedBeatEvents: Long
        get() = 0
    val renderedRhythmEvents: Long
        get() = 0
    fun prepare(maximumBlockFrames: Int)
    fun reset()
    fun setMuted(muted: Boolean) {}
    fun render(startFrame: Long, output: ShortArray, frameCount: Int): FrameRenderResult
}

class FramePcmRenderer(
    private var eventSource: FrameRangeEventSource,
    private val waveforms: RenderWaveforms,
    maximumActiveVoices: Int,
    private val eventCapture: RenderedEventRing? = null,
    maximumBlockEvents: Int = DEFAULT_MAXIMUM_BLOCK_EVENTS
) : FrameRangeEventConsumer, PcmFrameRenderer {
    private val activeRoles = ByteArray(maximumActiveVoices)
    private val activePositions = IntArray(maximumActiveVoices)
    private var accumulator = IntArray(0)
    private var blockStartFrame = 0L
    private var blockFrameCount = 0
    private var result = FrameRenderResult.COMPLETE
    private var hasExpectedFrame = false
    private var nextExpectedFrame = 0L
    private var blockBeatEvents = 0L
    private var blockRhythmEvents = 0L
    private var liveMuted: Boolean? = null
    private val blockSessionIds = LongArray(maximumBlockEvents)
    private val blockEventSequences = LongArray(maximumBlockEvents)
    private val blockIntendedFrames = LongArray(maximumBlockEvents)
    private val blockRoles = arrayOfNulls<MusicalEventRole>(maximumBlockEvents)
    private val blockMuted = BooleanArray(maximumBlockEvents)
    private var blockCapturedEvents = 0

    override var renderedBeatEvents = 0L
        private set

    override var renderedRhythmEvents = 0L
        private set

    override fun accept(
        sessionId: Long,
        eventSequence: Long,
        intendedFrame: Long,
        primaryRole: MusicalEventRole,
        primarySound: SoundRole,
        secondaryRole: MusicalEventRole?,
        secondarySound: SoundRole?,
        muted: Boolean
    ): Boolean {
        if (eventCapture != null) {
            captureBlockEvent(sessionId, eventSequence, intendedFrame, primaryRole, muted)
            if (secondaryRole != null) {
                captureBlockEvent(sessionId, eventSequence, intendedFrame, secondaryRole, muted)
            }
        }
        if (!(liveMuted ?: muted)) {
            if (addVoice(primarySound, intendedFrame)) countBlockEvent(primarySound)
            if (result == FrameRenderResult.COMPLETE && secondarySound != null) {
                if (addVoice(secondarySound, intendedFrame)) countBlockEvent(secondarySound)
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

    override fun setMuted(muted: Boolean) {
        liveMuted = muted
    }

    fun replaceEventSource(replacement: FrameRangeEventSource) {
        eventSource = replacement
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
        blockBeatEvents = 0
        blockRhythmEvents = 0
        blockCapturedEvents = 0
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
        renderedBeatEvents = Math.addExact(renderedBeatEvents, blockBeatEvents)
        renderedRhythmEvents = Math.addExact(renderedRhythmEvents, blockRhythmEvents)
        commitBlockEvents()
        hasExpectedFrame = true
        nextExpectedFrame = endFrame
        return result
    }

    private fun addVoice(role: SoundRole, intendedFrame: Long): Boolean {
        val offset = intendedFrame - blockStartFrame
        if (offset < 0 || offset >= blockFrameCount) {
            result = FrameRenderResult.EVENT_SOURCE_FAILED
            return false
        }
        var slot = 0
        while (slot < activeRoles.size && activeRoles[slot] != NO_VOICE) slot++
        if (slot == activeRoles.size) {
            result = FrameRenderResult.VOICE_CAPACITY_EXCEEDED
            return false
        }
        activeRoles[slot] = if (role == SoundRole.BEAT) BEAT_VOICE else RHYTHM_VOICE
        activePositions[slot] = 0
        mixVoice(slot, offset.toInt())
        return true
    }

    private fun countBlockEvent(role: SoundRole) {
        if (role == SoundRole.BEAT) blockBeatEvents++ else blockRhythmEvents++
    }

    private fun captureBlockEvent(
        sessionId: Long,
        eventSequence: Long,
        intendedFrame: Long,
        role: MusicalEventRole,
        muted: Boolean
    ) {
        if (blockCapturedEvents >= blockSessionIds.size) return
        val index = blockCapturedEvents++
        blockSessionIds[index] = sessionId
        blockEventSequences[index] = eventSequence
        blockIntendedFrames[index] = intendedFrame
        blockRoles[index] = role
        blockMuted[index] = liveMuted ?: muted
    }

    private fun commitBlockEvents() {
        val capture = eventCapture ?: return
        var index = 0
        while (index < blockCapturedEvents) {
            capture.record(
                blockSessionIds[index],
                blockEventSequences[index],
                checkNotNull(blockRoles[index]),
                blockIntendedFrames[index],
                blockMuted[index]
            )
            index++
        }
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
        const val DEFAULT_MAXIMUM_BLOCK_EVENTS = 64
    }
}
