package com.bfunkstudios.beatclikr.ui

import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackTransportState

internal fun PlaybackTransportState.isModeActive(mode: PlaybackMode): Boolean {
    val session = this as? PlaybackTransportState.SessionState ?: return false
    return session.context.mode == mode &&
        this !is PlaybackTransportState.Stopping &&
        this !is PlaybackTransportState.Interrupted &&
        this !is PlaybackTransportState.Failed
}

internal fun PlaybackTransportState.isModeTransitioning(mode: PlaybackMode): Boolean {
    val session = this as? PlaybackTransportState.SessionState ?: return false
    return session.context.mode == mode &&
        (this is PlaybackTransportState.Preparing ||
            this is PlaybackTransportState.Starting ||
            this is PlaybackTransportState.Stopping)
}
