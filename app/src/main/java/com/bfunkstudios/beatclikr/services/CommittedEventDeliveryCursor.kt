package com.bfunkstudios.beatclikr.services

data class CommittedEventDeliveryGap(
    val expectedSequence: Long,
    val observedSequence: Long
) {
    val missingCount: Long = observedSequence - expectedSequence
}

sealed interface CommittedEventDeliveryResult {
    data object Accepted : CommittedEventDeliveryResult
    data object Duplicate : CommittedEventDeliveryResult
    data class Gap(val detail: CommittedEventDeliveryGap) : CommittedEventDeliveryResult
}

class CommittedEventDeliveryCursor(initialSequence: Long = 0) {
    init {
        require(initialSequence >= 0) { "Initial event sequence must not be negative" }
    }

    private var sequence = initialSequence

    fun accept(event: PlaybackCommittedEvent): CommittedEventDeliveryResult {
        if (event.sequence <= sequence) return CommittedEventDeliveryResult.Duplicate
        val expected = sequence + 1
        sequence = event.sequence
        return if (event.sequence == expected) {
            CommittedEventDeliveryResult.Accepted
        } else {
            CommittedEventDeliveryResult.Gap(
                CommittedEventDeliveryGap(expected, event.sequence)
            )
        }
    }
}

inline fun deliverCommittedEvent(
    cursor: CommittedEventDeliveryCursor,
    event: PlaybackCommittedEvent,
    onGap: (CommittedEventDeliveryGap) -> Unit
): Boolean = when (val delivery = cursor.accept(event)) {
    CommittedEventDeliveryResult.Accepted -> true
    CommittedEventDeliveryResult.Duplicate -> false
    is CommittedEventDeliveryResult.Gap -> {
        onGap(delivery.detail)
        false
    }
}
