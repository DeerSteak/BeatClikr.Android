package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.PlaybackLifecycleBatch
import com.bfunkstudios.beatclikr.services.PlaybackLifecycleCheckpoint
import com.bfunkstudios.beatclikr.services.PlaybackLifecycleObservation
import com.bfunkstudios.beatclikr.services.PlaybackTransportState
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlaybackLifecycleObservation : PlaybackLifecycleObservation {
    override val lifecycleCheckpoint = MutableStateFlow(
        PlaybackLifecycleCheckpoint(0, PlaybackTransportState.Idle)
    )

    override fun lifecycleTransitionsAfter(sequence: Long) = PlaybackLifecycleBatch(
        emptyList(),
        lifecycleCheckpoint.value
    )

    override fun acknowledgeLifecycleTransitionsThrough(sequence: Long) = Unit
}
