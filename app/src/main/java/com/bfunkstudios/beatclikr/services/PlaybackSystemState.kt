package com.bfunkstudios.beatclikr.services

import android.media.session.PlaybackState

internal fun PlaybackTransportState.toSystemPlaybackState(): PlaybackState {
    val state = when (this) {
        is PlaybackTransportState.Preparing,
        is PlaybackTransportState.Starting -> PlaybackState.STATE_CONNECTING
        is PlaybackTransportState.Playing -> PlaybackState.STATE_PLAYING
        PlaybackTransportState.Idle,
        is PlaybackTransportState.Stopping,
        is PlaybackTransportState.Interrupted,
        is PlaybackTransportState.Failed -> PlaybackState.STATE_STOPPED
    }
    return PlaybackState.Builder()
        .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
        .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
        .build()
}

internal fun playbackIntentForServiceAction(action: String?): PlaybackIntent? =
    if (action == PlaybackForegroundService.ACTION_STOP) PlaybackIntent.Stop else null
