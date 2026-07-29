package com.bfunkstudios.beatclikr.music

sealed interface ActivePlaybackMode {
    data class Standard(
        val configuration: StandardMetronomeConfiguration,
        val ramp: TempoRampConfiguration?
    ) : ActivePlaybackMode

    data class Polyrhythm(
        val configuration: PolyrhythmConfiguration
    ) : ActivePlaybackMode
}

data class PlaybackSnapshot(
    val soundConfiguration: SoundConfiguration,
    val requestedMute: Boolean = false,
    val logicalPlaybackID: LogicalPlaybackID? = null,
    val origin: SessionOrigin? = null,
    val mode: ActivePlaybackMode? = null,
    val lastCommandSequence: CommandSequence? = null
) {
    init {
        require((logicalPlaybackID == null) == (origin == null)) {
            "Logical playback and phase origin must be present together"
        }
        require((origin == null) == (mode == null)) {
            "Phase origin and active mode must be present together"
        }
    }
}

sealed interface BoundaryResult {
    data class Applied(
        val snapshot: PlaybackSnapshot,
        val restarted: Boolean
    ) : BoundaryResult

    data class PreparationRequired(
        val commandSequences: Set<CommandSequence>
    ) : BoundaryResult
}

object CommandBoundary {
    fun apply(
        current: PlaybackSnapshot,
        commands: List<PlaybackCommand>,
        restartOrigin: SessionOrigin,
        preparedSoundCommands: Set<CommandSequence> = emptySet()
    ): BoundaryResult {
        require(commands.isNotEmpty()) { "A boundary must contain at least one command" }
        requireStrictSequence(current, commands)
        requireTargetSession(current, commands, restartOrigin)
        val missingPreparation = commands
            .filter { it is SetSound || it is SetSoundBank }
            .map { it.metadata.commandSequence }
            .filterNot(preparedSoundCommands::contains)
            .toSet()
        if (missingPreparation.isNotEmpty()) {
            return BoundaryResult.PreparationRequired(missingPreparation)
        }

        var next = current
        var restartRequested = false
        commands.forEach { command ->
            when (command) {
                is StartStandard -> {
                    next = next.copy(
                        logicalPlaybackID = command.logicalPlaybackID,
                        origin = restartOrigin,
                        mode = ActivePlaybackMode.Standard(
                            command.configuration.withMute(next.requestedMute),
                            command.ramp
                        )
                    )
                    restartRequested = true
                }
                is StartPolyrhythm -> {
                    next = next.copy(
                        logicalPlaybackID = command.logicalPlaybackID,
                        origin = restartOrigin,
                        mode = ActivePlaybackMode.Polyrhythm(
                            command.configuration.withMute(next.requestedMute)
                        )
                    )
                    restartRequested = true
                }
                is Stop -> {
                    next = next.copy(logicalPlaybackID = null, origin = null, mode = null)
                    restartRequested = false
                }
                is SetTempo -> {
                    next = next.updateMode(
                        standard = { it.copy(configuration = it.configuration.copy(bpm = command.bpm)) },
                        polyrhythm = { it.copy(configuration = it.configuration.copy(bpm = command.bpm)) }
                    )
                    restartRequested = next.mode != null
                }
                is SetGroove -> {
                    next = next.updateStandard {
                        it.copy(configuration = it.configuration.copy(timing = StandardTiming.Regular(command.subdivision)))
                    }
                    restartRequested = true
                }
                is SetPattern -> {
                    next = next.updateStandard {
                        it.copy(
                            configuration = it.configuration.copy(
                                timing = StandardTiming.Additive(command.stepUnit, command.accents)
                            )
                        )
                    }
                    restartRequested = true
                }
                is SetSound -> {
                    next = next.copy(
                        soundConfiguration = when (command.role) {
                            SoundRole.BEAT -> next.soundConfiguration.copy(beatSound = command.soundID)
                            SoundRole.RHYTHM -> next.soundConfiguration.copy(rhythmSound = command.soundID)
                        }
                    )
                    restartRequested = next.mode != null
                }
                is SetSoundBank -> {
                    next = next.copy(
                        soundConfiguration = next.soundConfiguration.copy(soundBank = command.soundBank)
                    )
                    restartRequested = next.mode != null
                }
                is SetMute -> next = next.copy(requestedMute = command.muteMetronome)
                is SetPolyrhythm -> {
                    next = next.updatePolyrhythm {
                        it.copy(
                            configuration = it.configuration.copy(
                                beats = command.beats,
                                against = command.against
                            )
                        )
                    }
                    restartRequested = true
                }
                is SetRamp -> {
                    next = next.updateStandard { it.copy(ramp = command.ramp) }
                    restartRequested = true
                }
            }
        }
        if (restartRequested && next.mode != null) {
            require(current.origin == null || restartOrigin.sessionID != current.origin.sessionID) {
                "A playback restart requires a new phase session ID"
            }
            next = next.copy(
                origin = restartOrigin,
                mode = next.mode.withMute(next.requestedMute)
            )
        }
        next = next.copy(lastCommandSequence = commands.last().metadata.commandSequence)
        return BoundaryResult.Applied(next, restartRequested)
    }

    private fun requireStrictSequence(
        current: PlaybackSnapshot,
        commands: List<PlaybackCommand>
    ) {
        val startsNewLogicalPlayback = commands.any { it is StartStandard || it is StartPolyrhythm }
        if (!startsNewLogicalPlayback) {
            current.lastCommandSequence?.let { previous ->
                require(commands.first().metadata.commandSequence > previous) {
                    "Command sequence must advance across boundaries"
                }
            }
        }
        commands.zipWithNext().forEach { (first, second) ->
            require(first.metadata.commandSequence < second.metadata.commandSequence) {
                "Boundary commands must be strictly sequenced"
            }
        }
    }

    private fun requireTargetSession(
        current: PlaybackSnapshot,
        commands: List<PlaybackCommand>,
        restartOrigin: SessionOrigin
    ) {
        val target = current.origin?.sessionID ?: restartOrigin.sessionID
        require(commands.all { it.metadata.sessionID == target }) {
            "Every command must target the active boundary session"
        }
    }
}

private fun PlaybackSnapshot.updateMode(
    standard: (ActivePlaybackMode.Standard) -> ActivePlaybackMode.Standard,
    polyrhythm: (ActivePlaybackMode.Polyrhythm) -> ActivePlaybackMode.Polyrhythm
): PlaybackSnapshot =
    copy(
        mode = when (val active = mode) {
            is ActivePlaybackMode.Standard -> standard(active)
            is ActivePlaybackMode.Polyrhythm -> polyrhythm(active)
            null -> null
        }
    )

private fun PlaybackSnapshot.updateStandard(
    transform: (ActivePlaybackMode.Standard) -> ActivePlaybackMode.Standard
): PlaybackSnapshot {
    val active = requireNotNull(mode as? ActivePlaybackMode.Standard) {
        "Command requires active standard playback"
    }
    return copy(mode = transform(active))
}

private fun PlaybackSnapshot.updatePolyrhythm(
    transform: (ActivePlaybackMode.Polyrhythm) -> ActivePlaybackMode.Polyrhythm
): PlaybackSnapshot {
    val active = requireNotNull(mode as? ActivePlaybackMode.Polyrhythm) {
        "Command requires active polyrhythm playback"
    }
    return copy(mode = transform(active))
}

private fun ActivePlaybackMode.withMute(mute: Boolean): ActivePlaybackMode =
    when (this) {
        is ActivePlaybackMode.Standard -> copy(configuration = configuration.withMute(mute))
        is ActivePlaybackMode.Polyrhythm -> copy(configuration = configuration.withMute(mute))
    }

private fun StandardMetronomeConfiguration.withMute(mute: Boolean): StandardMetronomeConfiguration =
    copy(muteMetronome = mute)

private fun PolyrhythmConfiguration.withMute(mute: Boolean): PolyrhythmConfiguration =
    copy(muteMetronome = mute)
