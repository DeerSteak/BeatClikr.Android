package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile

internal data class SoundPreparationDecision(
    val adopted: ActiveSoundConfiguration?,
    val failure: SoundPreparationFailure?,
    val requestSequence: Long?
)

internal class PlaybackSoundState(initial: RequestedSoundConfiguration) {
    var requested = initial
        private set
    private var latestRequestSequence = 0L

    fun select(
        sequence: Long,
        bank: SoundBank? = null,
        beat: SoundFile? = null,
        rhythm: SoundFile? = null
    ): RequestedSoundConfiguration {
        latestRequestSequence = sequence
        requested = requested.copy(
            bank = bank ?: requested.bank,
            beatSound = beat ?: requested.beatSound,
            rhythmSound = rhythm ?: requested.rhythmSound
        )
        return requested
    }

    fun apply(
        publication: SoundPreparationPublication,
        sessionId: PlaybackSessionId?
    ): SoundPreparationDecision? {
        if (publication.requestSequence?.let { it < latestRequestSequence } == true) return null
        if (publication.sessionId != null && publication.sessionId != sessionId) return null
        val active = publication.active
        val adopted = active?.takeIf {
            publication.adopted && publication.sessionId == sessionId &&
                it.bank == requested.bank &&
                it.beatSound == requested.beatSound &&
                it.rhythmSound == requested.rhythmSound
        }
        return SoundPreparationDecision(adopted, publication.failure, publication.requestSequence)
    }
}
