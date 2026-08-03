package com.bfunkstudios.beatclikr.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.annotation.StringRes
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.services.AudioOutputRoute
import com.bfunkstudios.beatclikr.services.PlaybackFailureReason
import com.bfunkstudios.beatclikr.services.PlaybackInterruptionReason

@Composable
internal fun VariableLatencyWarning(visible: Boolean) {
    if (!visible) return
    val warning = stringResource(R.string.bluetooth_latency_warning)
    Text(
        text = warning,
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .testTag("bluetooth_latency_warning")
            .semantics { contentDescription = warning }
    )
}

@Composable
internal fun PlaybackStatusText(status: PlaybackUiStatus?) {
    status ?: return
    val text = stringResource(
        when (status) {
            PlaybackUiStatus.PREPARING -> R.string.playback_status_preparing
            PlaybackUiStatus.PLAYING -> R.string.playback_status_playing
            PlaybackUiStatus.STOPPING -> R.string.playback_status_stopping
            PlaybackUiStatus.INTERRUPTED -> R.string.playback_status_interrupted
            PlaybackUiStatus.FAILED -> R.string.playback_status_failed
        }
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .testTag("playback_status")
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
internal fun PlaybackDiagnosticText(diagnostic: PlaybackUiDiagnostic?) {
    diagnostic ?: return
    Text(
        text = diagnostic.text(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("playback_diagnostic")
            .semantics { liveRegion = LiveRegionMode.Assertive }
    )
}

@Composable
private fun PlaybackUiDiagnostic.text(): String = when (this) {
    is PlaybackUiDiagnostic.Failure -> when (val failure = reason) {
        PlaybackFailureReason.AudioFocusUnavailable ->
            stringResource(R.string.playback_focus_unavailable)
        PlaybackFailureReason.RouteUnavailable ->
            stringResource(R.string.playback_route_unavailable)
        is PlaybackFailureReason.SoundPreparation ->
            stringResource(R.string.playback_sound_unavailable)
        is PlaybackFailureReason.StreamStart ->
            stringResource(R.string.playback_stream_start_failed)
        is PlaybackFailureReason.Engine ->
            stringResource(R.string.playback_engine_failed)
    }
    is PlaybackUiDiagnostic.Interruption -> when (val interruption = reason) {
        PlaybackInterruptionReason.AudioFocusLost ->
            stringResource(R.string.playback_focus_lost)
        is PlaybackInterruptionReason.RouteUnavailable ->
            stringResource(R.string.playback_route_lost)
        is PlaybackInterruptionReason.RouteChanged -> stringResource(
            R.string.playback_route_changed,
            stringResource(interruption.previous.labelResource()),
            stringResource(interruption.current.labelResource())
        )
        is PlaybackInterruptionReason.EngineStopped ->
            stringResource(R.string.playback_engine_stopped)
    }
}

@StringRes
private fun AudioOutputRoute.labelResource(): Int = when (this) {
    AudioOutputRoute.BUILT_IN -> R.string.audio_route_built_in
    AudioOutputRoute.WIRED -> R.string.audio_route_wired
    AudioOutputRoute.USB -> R.string.audio_route_usb
    AudioOutputRoute.BLUETOOTH -> R.string.audio_route_bluetooth
    AudioOutputRoute.HDMI -> R.string.audio_route_hdmi
    AudioOutputRoute.REMOTE -> R.string.audio_route_remote
    AudioOutputRoute.OTHER -> R.string.audio_route_other
    AudioOutputRoute.UNKNOWN -> R.string.audio_route_unknown
}
