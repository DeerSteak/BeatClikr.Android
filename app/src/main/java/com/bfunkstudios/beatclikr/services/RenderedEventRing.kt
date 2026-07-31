package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.MusicalEventRole

data class RenderedFrameEvent(
    val captureSequence: Long,
    val sessionId: Long,
    val eventSequence: Long,
    val role: MusicalEventRole,
    val intendedFrame: Long,
    val muted: Boolean,
    val roleIndex: Int = 0
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

    private val slotSequences = Array(capacity) { SlotSequence(UNPUBLISHED) }
    private val sessionIds = LongArray(capacity)
    private val eventSequences = LongArray(capacity)
    private val intendedFrames = LongArray(capacity)
    private val roles = ByteArray(capacity)
    private val roleIndices = IntArray(capacity)
    private val muted = BooleanArray(capacity)
    private var producerSequence = 0L

    @Volatile
    private var publishedSequence = 0L

    fun record(
        sessionId: Long,
        eventSequence: Long,
        role: MusicalEventRole,
        intendedFrame: Long,
        isMuted: Boolean,
        roleIndex: Int = 0
    ) = recordOrdinal(
        sessionId,
        eventSequence,
        role.ordinal,
        intendedFrame,
        isMuted,
        roleIndex
    )

    internal fun recordOrdinal(
        sessionId: Long,
        eventSequence: Long,
        roleOrdinal: Int,
        intendedFrame: Long,
        isMuted: Boolean,
        roleIndex: Int = 0
    ) {
        val sequence = producerSequence
        val index = (sequence % capacity).toInt()
        slotSequences[index].value = UNPUBLISHED
        sessionIds[index] = sessionId
        eventSequences[index] = eventSequence
        intendedFrames[index] = intendedFrame
        roles[index] = roleOrdinal.toByte()
        roleIndices[index] = roleIndex
        muted[index] = isMuted
        slotSequences[index].value = sequence
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
            if (slotSequences[index].value == sequence) {
                afterSlotValidation?.invoke()
                val record = RenderedFrameEvent(
                    sequence,
                    sessionIds[index],
                    eventSequences[index],
                    MusicalEventRole.entries[roles[index].toInt()],
                    intendedFrames[index],
                    muted[index],
                    roleIndices[index]
                )
                if (slotSequences[index].value == sequence) {
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

    private class SlotSequence(@Volatile var value: Long)
}
