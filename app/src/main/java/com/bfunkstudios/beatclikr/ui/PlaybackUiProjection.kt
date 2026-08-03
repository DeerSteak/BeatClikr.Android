package com.bfunkstudios.beatclikr.ui

import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackFailureReason
import com.bfunkstudios.beatclikr.services.PlaybackInterruptionReason
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.PlaybackTransportState
import com.bfunkstudios.beatclikr.services.hasVariableOutputLatency

internal fun PlaybackTransportState.isModeActive(mode: PlaybackMode): Boolean {
    val session = this as? PlaybackTransportState.SessionState ?: return false
    return session.context.mode == mode &&
        this !is PlaybackTransportState.Stopping &&
        this !is PlaybackTransportState.Interrupted &&
        this !is PlaybackTransportState.Failed
}

internal fun PlaybackTransportState.sessionIdFor(mode: PlaybackMode): PlaybackSessionId? =
    (this as? PlaybackTransportState.SessionState)
        ?.context
        ?.takeIf { it.mode == mode }
        ?.sessionId

internal fun PlaybackTransportState.isModeTransitioning(mode: PlaybackMode): Boolean {
    val session = this as? PlaybackTransportState.SessionState ?: return false
    return session.context.mode == mode &&
        (this is PlaybackTransportState.Preparing ||
            this is PlaybackTransportState.Starting ||
            this is PlaybackTransportState.Stopping)
}

internal fun PlaybackTransportState.hasVariableOutputLatency(mode: PlaybackMode): Boolean {
    val playing = this as? PlaybackTransportState.Playing ?: return false
    return playing.context.mode == mode && playing.context.hasVariableOutputLatency
}

sealed interface PlaybackUiDiagnostic {
    data class Failure(val reason: PlaybackFailureReason) : PlaybackUiDiagnostic
    data class Interruption(val reason: PlaybackInterruptionReason) : PlaybackUiDiagnostic
}

enum class PlaybackUiStatus { PREPARING, PLAYING, STOPPING, INTERRUPTED, FAILED }

internal fun PlaybackTransportState.uiStatus(mode: PlaybackMode): PlaybackUiStatus? {
    val session = this as? PlaybackTransportState.SessionState ?: return null
    if (session.context.mode != mode) return null
    return when (this) {
        is PlaybackTransportState.Preparing,
        is PlaybackTransportState.Starting -> PlaybackUiStatus.PREPARING
        is PlaybackTransportState.Playing -> PlaybackUiStatus.PLAYING
        is PlaybackTransportState.Stopping -> PlaybackUiStatus.STOPPING
        is PlaybackTransportState.Interrupted -> PlaybackUiStatus.INTERRUPTED
        is PlaybackTransportState.Failed -> PlaybackUiStatus.FAILED
        PlaybackTransportState.Idle -> null
    }
}

internal fun PlaybackTransportState.updateDiagnostic(
    retained: PlaybackUiDiagnostic?
): PlaybackUiDiagnostic? = when (this) {
    is PlaybackTransportState.Failed -> PlaybackUiDiagnostic.Failure(reason)
    is PlaybackTransportState.Interrupted -> PlaybackUiDiagnostic.Interruption(reason)
    is PlaybackTransportState.Playing -> context.soundPreparationFailure?.let {
        PlaybackUiDiagnostic.Failure(PlaybackFailureReason.SoundPreparation(it))
    }
    else -> retained
}
