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

data class FrameEvent(
    val intendedFrame: Long,
    val primary: EventVoice,
    val secondary: EventVoice? = null
) {
    init {
        require(intendedFrame >= 0) { "Intended frame must not be negative" }
        require(secondary == null || secondary.role != primary.role) {
            "Coincident voices must have distinct roles"
        }
    }
}
