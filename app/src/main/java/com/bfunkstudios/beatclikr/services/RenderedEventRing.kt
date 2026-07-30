package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.MusicalEventRole
import java.util.concurrent.atomic.AtomicLongArray

data class RenderedFrameEvent(
    val captureSequence: Long,
    val sessionId: Long,
    val eventSequence: Long,
    val role: MusicalEventRole,
    val intendedFrame: Long,
    val muted: Boolean
)

data class RenderedEventBatch(
    val records: List<RenderedFrameEvent>,
    val nextCaptureSequence: Long,
    val droppedRecords: Long
)

class RenderedEventRing(private val capacity: Int) {
    init {
        require(capacity > 0) { "Rendered event capacity must be positive" }
    }

    private val slotSequences = AtomicLongArray(capacity)
    private val sessionIds = LongArray(capacity)
    private val eventSequences = LongArray(capacity)
    private val intendedFrames = LongArray(capacity)
    private val roles = ByteArray(capacity)
    private val muted = BooleanArray(capacity)
    private var producerSequence = 0L

    @Volatile
    private var publishedSequence = 0L

    init {
        var index = 0
        while (index < capacity) {
            slotSequences.set(index, UNPUBLISHED)
            index++
        }
    }

    fun record(
        sessionId: Long,
        eventSequence: Long,
        role: MusicalEventRole,
        intendedFrame: Long,
        isMuted: Boolean
    ) {
        val sequence = producerSequence
        val index = (sequence % capacity).toInt()
        sessionIds[index] = sessionId
        eventSequences[index] = eventSequence
        intendedFrames[index] = intendedFrame
        roles[index] = role.ordinal.toByte()
        muted[index] = isMuted
        slotSequences.lazySet(index, sequence)
        producerSequence = sequence + 1
        publishedSequence = producerSequence
    }

    fun drain(afterCaptureSequence: Long): RenderedEventBatch {
        require(afterCaptureSequence >= 0) { "Capture sequence must not be negative" }
        val end = publishedSequence
        val oldest = (end - capacity).coerceAtLeast(0)
        val start = maxOf(afterCaptureSequence, oldest)
        val records = ArrayList<RenderedFrameEvent>((end - start).toInt())
        var sequence = start
        while (sequence < end) {
            val index = (sequence % capacity).toInt()
            if (slotSequences.get(index) == sequence) {
                records += RenderedFrameEvent(
                    sequence,
                    sessionIds[index],
                    eventSequences[index],
                    MusicalEventRole.entries[roles[index].toInt()],
                    intendedFrames[index],
                    muted[index]
                )
            }
            sequence++
        }
        return RenderedEventBatch(
            records,
            end,
            (oldest - afterCaptureSequence).coerceAtLeast(0)
        )
    }

    private companion object {
        const val UNPUBLISHED = -1L
    }
}
