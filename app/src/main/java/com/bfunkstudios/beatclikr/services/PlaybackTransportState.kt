package com.bfunkstudios.beatclikr.services

@JvmInline
value class PlaybackSessionId(val value: Long) {
    init {
        require(value > 0) { "Playback session ID must be positive" }
    }
}

enum class PlaybackStartOrigin {
    USER,
    PLAYLIST,
    RESTORE,
    SERVICE
}

enum class PlaybackPrerequisite {
    AUDIO_FOCUS,
    ROUTE_READY
}

data class PlaybackPrerequisites(
    val audioFocusReady: Boolean,
    val routeReady: Boolean
) {
    val missing: Set<PlaybackPrerequisite>
        get() = buildSet {
            if (!audioFocusReady) add(PlaybackPrerequisite.AUDIO_FOCUS)
            if (!routeReady) add(PlaybackPrerequisite.ROUTE_READY)
        }

    companion object {
        val READY = PlaybackPrerequisites(audioFocusReady = true, routeReady = true)
    }
}

sealed interface CommittedPlaybackConfiguration {
    val muted: Boolean

    data class Standard(
        val bpm: Float,
        val subdivisions: Int,
        val accentPattern: List<Boolean>?,
        val alternateSixteenth: Boolean,
        override val muted: Boolean
    ) : CommittedPlaybackConfiguration

    data class Polyrhythm(
        val bpm: Float,
        val beats: Int,
        val against: Int,
        override val muted: Boolean
    ) : CommittedPlaybackConfiguration
}

data class PlaybackSessionContext(
    val sessionId: PlaybackSessionId,
    val mode: PlaybackMode,
    val configuration: CommittedPlaybackConfiguration,
    val audibleSounds: ActiveSoundConfiguration? = null,
    val route: AudioOutputRoute? = null,
    val backend: AudioBackendType? = null,
    val startOrigin: PlaybackStartOrigin
) {
    init {
        require(mode != PlaybackMode.NONE) { "Playback session requires an active mode" }
        require(
            (mode == PlaybackMode.STANDARD &&
                configuration is CommittedPlaybackConfiguration.Standard) ||
                (mode == PlaybackMode.POLYRHYTHM &&
                    configuration is CommittedPlaybackConfiguration.Polyrhythm)
        ) { "Playback mode and configuration must match" }
    }
}

sealed interface PlaybackInterruptionReason {
    data object AudioFocusLost : PlaybackInterruptionReason
    data object RouteLost : PlaybackInterruptionReason
    data class RouteChanged(
        val previous: AudioOutputRoute,
        val current: AudioOutputRoute
    ) : PlaybackInterruptionReason
    data class EngineStopped(val diagnostic: String) : PlaybackInterruptionReason
}

val PlaybackSessionContext.hasVariableOutputLatency: Boolean
    get() = route == AudioOutputRoute.BLUETOOTH

sealed interface PlaybackFailureReason {
    data object AudioFocusUnavailable : PlaybackFailureReason

    data class PrerequisiteUnavailable(
        val missing: Set<PlaybackPrerequisite>
    ) : PlaybackFailureReason

    data class SoundPreparation(val failure: SoundPreparationFailure) :
        PlaybackFailureReason

    data class Engine(val diagnostic: String) : PlaybackFailureReason
}

sealed interface PlaybackTransportState {
    data object Idle : PlaybackTransportState

    sealed interface SessionState : PlaybackTransportState {
        val context: PlaybackSessionContext
    }

    data class Preparing(
        override val context: PlaybackSessionContext,
        val prerequisites: PlaybackPrerequisites
    ) : SessionState

    data class Starting(override val context: PlaybackSessionContext) : SessionState {
        init {
            requireNotNull(context.audibleSounds) {
                "Starting requires prepared audible sounds"
            }
        }
    }

    data class Playing(override val context: PlaybackSessionContext) : SessionState {
        init {
            requireNotNull(context.audibleSounds) {
                "Playing requires prepared audible sounds"
            }
            requireNotNull(context.route) { "Playing requires a committed output route" }
            requireNotNull(context.backend) { "Playing requires a committed audio backend" }
        }
    }
    data class Stopping(override val context: PlaybackSessionContext) : SessionState

    data class Interrupted(
        override val context: PlaybackSessionContext,
        val reason: PlaybackInterruptionReason
    ) : SessionState

    data class Failed(
        override val context: PlaybackSessionContext,
        val reason: PlaybackFailureReason
    ) : SessionState
}

class IllegalPlaybackTransition(
    from: PlaybackTransportState,
    to: PlaybackTransportState
) : IllegalStateException(
    "Illegal playback transition: ${from::class.simpleName} -> ${to::class.simpleName}"
)

object PlaybackTransportTransitions {
    fun requireLegal(
        from: PlaybackTransportState,
        to: PlaybackTransportState
    ): PlaybackTransportState {
        if (!isLegal(from, to)) throw IllegalPlaybackTransition(from, to)
        return to
    }

    fun isLegal(from: PlaybackTransportState, to: PlaybackTransportState): Boolean {
        if (from == to && (from is PlaybackTransportState.Idle ||
                from is PlaybackTransportState.Stopping)) {
            return true
        }
        val sameSession = from.sessionIdOrNull() == to.sessionIdOrNull()
        if (sameSession && from::class == to::class &&
            from is PlaybackTransportState.SessionState) {
            return from is PlaybackTransportState.Preparing ||
                from is PlaybackTransportState.Starting ||
                from is PlaybackTransportState.Playing
        }
        return when (from) {
            PlaybackTransportState.Idle -> to is PlaybackTransportState.Preparing
            is PlaybackTransportState.Preparing ->
                sameSession && (
                    to is PlaybackTransportState.Starting &&
                        from.prerequisites.missing.isEmpty() ||
                        to is PlaybackTransportState.Stopping ||
                        to is PlaybackTransportState.Failed
                    )
            is PlaybackTransportState.Starting ->
                sameSession && (
                    to is PlaybackTransportState.Playing ||
                        to is PlaybackTransportState.Stopping ||
                        to is PlaybackTransportState.Failed
                    )
            is PlaybackTransportState.Playing ->
                sameSession && (
                    to is PlaybackTransportState.Stopping ||
                        to is PlaybackTransportState.Interrupted ||
                        to is PlaybackTransportState.Failed
                    )
            is PlaybackTransportState.Stopping ->
                to is PlaybackTransportState.Idle ||
                    to is PlaybackTransportState.Preparing &&
                    to.context.sessionId != from.context.sessionId
            is PlaybackTransportState.Interrupted ->
                to is PlaybackTransportState.Idle
            is PlaybackTransportState.Failed ->
                to is PlaybackTransportState.Idle
        }
    }

    private fun PlaybackTransportState.sessionIdOrNull(): PlaybackSessionId? =
        (this as? PlaybackTransportState.SessionState)?.context?.sessionId
}
