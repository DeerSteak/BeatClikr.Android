package com.bfunkstudios.beatclikr.music

enum class MusicalEventRole {
    STANDARD,
    POLYRHYTHM_BEAT,
    POLYRHYTHM_RHYTHM
}

enum class SoundRole {
    BEAT,
    RHYTHM
}

enum class BeatIdentity {
    BEAT,
    ACCENT,
    SUBDIVISION
}

data class CyclePosition(
    val cycleIndex: Long,
    val index: Int
) {
    init {
        require(cycleIndex >= 0) { "Cycle index must not be negative" }
        require(index >= 0) { "Event index must not be negative" }
    }
}

data class EventVoice(
    val role: MusicalEventRole,
    val soundRole: SoundRole,
    val beatIdentity: BeatIdentity,
    val position: CyclePosition
)

@JvmInline
value class SessionID(val value: Long) {
    init {
        require(value >= 0) { "Session ID must not be negative" }
    }
}

data class SessionOrigin(
    val sessionID: SessionID,
    val originFrame: Long
) {
    init {
        require(originFrame >= 0) { "Origin frame must not be negative" }
    }

    fun firstEventSequence(): EventSequence = EventSequence(sessionID, 0)
}

data class EventSequence(
    val sessionID: SessionID,
    val index: Long
) {
    init {
        require(index >= 0) { "Event sequence index must not be negative" }
    }

    fun next(): EventSequence {
        require(index < Long.MAX_VALUE) { "Event sequence exhausted" }
        return copy(index = index + 1)
    }
}

data class FrameEvent(
    val sequence: EventSequence,
    val intendedFrame: Long,
    val primary: EventVoice,
    val secondary: EventVoice? = null,
    val muteMetronome: Boolean = false
) {
    init {
        require(intendedFrame >= 0) { "Intended frame must not be negative" }
        require(secondary == null || secondary.role != primary.role) {
            "Coincident voices must have distinct roles"
        }
    }
}
