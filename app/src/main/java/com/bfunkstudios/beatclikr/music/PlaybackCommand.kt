package com.bfunkstudios.beatclikr.music

@JvmInline
value class CommandSequence(val value: Long) : Comparable<CommandSequence> {
    init {
        require(value >= 0) { "Command sequence must not be negative" }
    }

    override fun compareTo(other: CommandSequence): Int = value.compareTo(other.value)
}

@JvmInline
value class LogicalPlaybackID(val value: Long) {
    init {
        require(value >= 0) { "Logical playback ID must not be negative" }
    }
}

data class CommandMetadata(
    val sessionID: SessionID,
    val commandSequence: CommandSequence,
    val submissionTimestampNanos: Long
) {
    init {
        require(submissionTimestampNanos >= 0) { "Submission timestamp must not be negative" }
    }
}

enum class SoundBank {
    ACOUSTIC,
    SYNTH
}

@JvmInline
value class SoundID(val value: String) {
    init {
        require(value.isNotBlank()) { "Sound ID must not be blank" }
    }
}

data class SoundConfiguration(
    val beatSound: SoundID,
    val rhythmSound: SoundID,
    val soundBank: SoundBank
)

sealed interface PlaybackCommand {
    val metadata: CommandMetadata
}

data class StartStandard(
    override val metadata: CommandMetadata,
    val logicalPlaybackID: LogicalPlaybackID,
    val configuration: StandardMetronomeConfiguration,
    val ramp: TempoRampConfiguration?
) : PlaybackCommand

data class StartPolyrhythm(
    override val metadata: CommandMetadata,
    val logicalPlaybackID: LogicalPlaybackID,
    val configuration: PolyrhythmConfiguration
) : PlaybackCommand

data class Stop(override val metadata: CommandMetadata) : PlaybackCommand

data class SetTempo(
    override val metadata: CommandMetadata,
    val bpm: ExactTempo
) : PlaybackCommand

data class SetGroove(
    override val metadata: CommandMetadata,
    val subdivision: StandardSubdivision
) : PlaybackCommand

data class SetPattern(
    override val metadata: CommandMetadata,
    val stepUnit: AdditiveStepUnit,
    val accents: AccentPattern
) : PlaybackCommand

data class SetSound(
    override val metadata: CommandMetadata,
    val role: SoundRole,
    val soundID: SoundID
) : PlaybackCommand

data class SetSoundBank(
    override val metadata: CommandMetadata,
    val soundBank: SoundBank
) : PlaybackCommand

data class SetMute(
    override val metadata: CommandMetadata,
    val muteMetronome: Boolean
) : PlaybackCommand

data class SetPolyrhythm(
    override val metadata: CommandMetadata,
    val beats: Int,
    val against: Int
) : PlaybackCommand {
    init {
        require(beats in PolyrhythmConfiguration.SUPPORTED_COUNT) { "Unsupported beats value" }
        require(against in PolyrhythmConfiguration.SUPPORTED_COUNT) { "Unsupported against value" }
    }
}

data class SetRamp(
    override val metadata: CommandMetadata,
    val ramp: TempoRampConfiguration?
) : PlaybackCommand
