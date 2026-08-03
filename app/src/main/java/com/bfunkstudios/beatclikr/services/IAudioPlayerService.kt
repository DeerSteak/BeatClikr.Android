package com.bfunkstudios.beatclikr.services

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackObservation {
    val transportState: StateFlow<PlaybackTransportState>
    val committedEvents: SharedFlow<PlaybackCommittedEvent>
}

interface IAudioPlayerService {
    fun submit(intent: PlaybackIntent): Long
    fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot?
    fun recentLifecycleDiagnostics(limit: Int = 20): List<PlaybackLifecycleDiagnostic> = emptyList()
    fun release()
}

@ConsistentCopyVisibility
data class PlaybackLifecycleDiagnostic internal constructor(
    val sequence: Long,
    val fromState: String,
    val toState: String
)
