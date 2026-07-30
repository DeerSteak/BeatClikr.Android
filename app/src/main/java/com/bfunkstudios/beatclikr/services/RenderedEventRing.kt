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
    ) = recordOrdinal(
        sessionId,
        eventSequence,
        role.ordinal,
        intendedFrame,
        isMuted
    )

    internal fun recordOrdinal(
        sessionId: Long,
        eventSequence: Long,
        roleOrdinal: Int,
        intendedFrame: Long,
        isMuted: Boolean
    ) {
        val sequence = producerSequence
        val index = (sequence % capacity).toInt()
        slotSequences.lazySet(index, UNPUBLISHED)
        sessionIds[index] = sessionId
        eventSequences[index] = eventSequence
        intendedFrames[index] = intendedFrame
        roles[index] = roleOrdinal.toByte()
        muted[index] = isMuted
        slotSequences.lazySet(index, sequence)
        producerSequence = sequence + 1
        publishedSequence = producerSequence
    }

    fun drop() {
        producerSequence++
        publishedSequence = producerSequence
    }

    fun drain(afterCaptureSequence: Long): RenderedEventBatch =
        drain(afterCaptureSequence, null)

    internal fun drain(
        afterCaptureSequence: Long,
        afterSlotValidation: (() -> Unit)?
    ): RenderedEventBatch {
        require(afterCaptureSequence >= 0) { "Capture sequence must not be negative" }
        val end = publishedSequence
        val oldest = (end - capacity).coerceAtLeast(0)
        val start = maxOf(afterCaptureSequence, oldest)
        val records = ArrayList<RenderedFrameEvent>((end - start).toInt())
        var dropped = (oldest - afterCaptureSequence).coerceAtLeast(0)
        var sequence = start
        while (sequence < end) {
            val index = (sequence % capacity).toInt()
            if (slotSequences.get(index) == sequence) {
                afterSlotValidation?.invoke()
                val record = RenderedFrameEvent(
                    sequence,
                    sessionIds[index],
                    eventSequences[index],
                    MusicalEventRole.entries[roles[index].toInt()],
                    intendedFrames[index],
                    muted[index]
                )
                if (slotSequences.get(index) == sequence) {
                    records += record
                } else {
                    dropped++
                }
            } else {
                dropped++
            }
            sequence++
        }
        return RenderedEventBatch(
            records,
            end,
            dropped
        )
    }

    private companion object {
        const val UNPUBLISHED = -1L
    }
}
