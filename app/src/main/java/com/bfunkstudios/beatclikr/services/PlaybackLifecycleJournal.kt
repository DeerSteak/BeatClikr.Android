package com.bfunkstudios.beatclikr.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class PlaybackLifecycleJournal(
    private val capacity: Int = 4_096
) {
    private val lock = Any()
    private val transitions = ArrayDeque<PlaybackStateTransition>()
    private var acknowledgedSequence = 0L
    private val mutableCheckpoint = MutableStateFlow(
        PlaybackLifecycleCheckpoint(0, PlaybackTransportState.Idle)
    )

    val checkpoint: StateFlow<PlaybackLifecycleCheckpoint> = mutableCheckpoint

    fun transitionsAfter(sequence: Long): PlaybackLifecycleBatch {
        require(sequence >= 0) { "Lifecycle sequence must not be negative" }
        return synchronized(lock) {
            require(sequence >= acknowledgedSequence) {
                "Lifecycle sequence $sequence was already acknowledged"
            }
            val oldestAvailable = transitions.firstOrNull()?.sequence
                ?: mutableCheckpoint.value.latestTransitionSequence + 1
            PlaybackLifecycleBatch(
                transitions.filter { it.sequence > sequence },
                mutableCheckpoint.value,
                if (sequence + 1 < oldestAvailable) {
                    PlaybackLifecycleGap(sequence, oldestAvailable)
                } else {
                    null
                }
            )
        }
    }

    fun acknowledgeThrough(sequence: Long) {
        require(sequence >= 0) { "Lifecycle sequence must not be negative" }
        synchronized(lock) {
            require(sequence <= mutableCheckpoint.value.latestTransitionSequence) {
                "Cannot acknowledge an unpublished lifecycle sequence"
            }
            if (sequence <= acknowledgedSequence) return
            while (transitions.firstOrNull()?.sequence?.let { it <= sequence } == true) {
                transitions.removeFirst()
            }
            acknowledgedSequence = sequence
        }
    }

    fun record(transition: PlaybackStateTransition) {
        synchronized(lock) {
            transitions += transition
            if (transitions.size > capacity) transitions.removeFirst()
            mutableCheckpoint.value = PlaybackLifecycleCheckpoint(
                transition.sequence,
                transition.to
            )
        }
    }
}
