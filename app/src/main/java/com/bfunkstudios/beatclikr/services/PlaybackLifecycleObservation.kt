package com.bfunkstudios.beatclikr.services

import kotlinx.coroutines.flow.StateFlow

data class PlaybackLifecycleCheckpoint(
    val latestTransitionSequence: Long,
    val state: PlaybackTransportState
)

data class PlaybackLifecycleBatch(
    val transitions: List<PlaybackStateTransition>,
    val checkpoint: PlaybackLifecycleCheckpoint
)

interface PlaybackLifecycleObservation {
    val lifecycleCheckpoint: StateFlow<PlaybackLifecycleCheckpoint>
    fun lifecycleTransitionsAfter(sequence: Long): PlaybackLifecycleBatch
}
