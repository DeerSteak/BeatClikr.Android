package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.AccentPattern
import com.bfunkstudios.beatclikr.music.AdditiveStepUnit
import com.bfunkstudios.beatclikr.music.ExactTempo
import com.bfunkstudios.beatclikr.music.PlaybackInputBoundary
import com.bfunkstudios.beatclikr.music.PlaybackInputFailure
import com.bfunkstudios.beatclikr.music.PlaybackInputResult
import com.bfunkstudios.beatclikr.music.PolyrhythmConfiguration
import com.bfunkstudios.beatclikr.music.SessionOrigin
import com.bfunkstudios.beatclikr.music.StandardMetronomeConfiguration
import com.bfunkstudios.beatclikr.music.StandardSubdivision
import com.bfunkstudios.beatclikr.music.StandardTiming

sealed interface FramePublicationResult {
    data class Ready(val factory: PcmFrameRendererFactory) : FramePublicationResult
    data class Rejected(
        val code: FramePublicationFailureCode,
        val cause: PlaybackInputFailure? = null
    ) : FramePublicationResult
}

enum class FramePublicationFailureCode {
    SOUNDS_UNAVAILABLE,
    INVALID_CONFIGURATION
}

object FramePlaybackPublicationBoundary {
    fun standard(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        muted: Boolean,
        origin: SessionOrigin,
        sounds: ActivePreparedSounds?
    ): FramePublicationResult = translate(sounds) { preparedSounds ->
        val timing = if (accentPattern == null) {
            val subdivision = StandardSubdivision.entries
                .firstOrNull { it.subdivisions == subdivisions }
                ?: throw IllegalArgumentException("Unsupported subdivision")
            StandardTiming.Regular(subdivision)
        } else {
            val stepUnit = AdditiveStepUnit.entries
                .firstOrNull { it.subdivisions == subdivisions }
                ?: throw IllegalArgumentException("Unsupported additive step")
            StandardTiming.Additive(stepUnit, AccentPattern.of(accentPattern))
        }
        StandardPreparedFrameRendererFactory(
            StandardMetronomeConfiguration(
                bpm = ExactTempo.fromFloat(bpm),
                timing = timing,
                alternateSixteenth = alternateSixteenth,
                muteMetronome = muted
            ),
            origin = origin,
            sounds = preparedSounds
        )
    }

    fun polyrhythm(
        bpm: Float,
        beats: Int,
        against: Int,
        muted: Boolean,
        origin: SessionOrigin,
        sounds: ActivePreparedSounds?
    ): FramePublicationResult = translate(sounds) { preparedSounds ->
        PolyrhythmPreparedFrameRendererFactory(
            PolyrhythmConfiguration(
                bpm = ExactTempo.fromFloat(bpm),
                beats = beats,
                against = against,
                muteMetronome = muted
            ),
            origin = origin,
            sounds = preparedSounds
        )
    }

    private fun translate(
        sounds: ActivePreparedSounds?,
        operation: (ActivePreparedSounds) -> PcmFrameRendererFactory
    ): FramePublicationResult {
        if (sounds == null) {
            return FramePublicationResult.Rejected(
                FramePublicationFailureCode.SOUNDS_UNAVAILABLE
            )
        }
        return when (val result = PlaybackInputBoundary.translate { operation(sounds) }) {
            is PlaybackInputResult.Accepted -> FramePublicationResult.Ready(result.value)
            is PlaybackInputResult.Rejected -> FramePublicationResult.Rejected(
                FramePublicationFailureCode.INVALID_CONFIGURATION,
                cause = result.failure
            )
        }
    }
}
