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
    fun standardConfiguration(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        muted: Boolean
    ): PlaybackInputResult<StandardMetronomeConfiguration> =
        PlaybackInputBoundary.translate {
            createStandardConfiguration(
                bpm,
                subdivisions,
                accentPattern,
                alternateSixteenth,
                muted
            )
        }

    fun standard(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        muted: Boolean,
        origin: SessionOrigin,
        sounds: ActivePreparedSounds?,
        startDelayMillis: Long = 0,
        eventCapture: RenderedEventRing? = null
    ): FramePublicationResult = translate(sounds) { preparedSounds ->
        StandardPreparedFrameRendererFactory(
            createStandardConfiguration(
                bpm,
                subdivisions,
                accentPattern,
                alternateSixteenth,
                muted
            ),
            origin = origin,
            sounds = preparedSounds,
            startDelayMillis = startDelayMillis,
            eventCapture = eventCapture
        )
    }

    private fun createStandardConfiguration(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        muted: Boolean
    ): StandardMetronomeConfiguration {
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
        return StandardMetronomeConfiguration(
            bpm = ExactTempo.fromFloat(bpm),
            timing = timing,
            alternateSixteenth = alternateSixteenth,
            muteMetronome = muted
        )
    }

    fun polyrhythm(
        bpm: Float,
        beats: Int,
        against: Int,
        muted: Boolean,
        origin: SessionOrigin,
        sounds: ActivePreparedSounds?,
        startDelayMillis: Long = 0,
        eventCapture: RenderedEventRing? = null
    ): FramePublicationResult = translate(sounds) { preparedSounds ->
        PolyrhythmPreparedFrameRendererFactory(
            createPolyrhythmConfiguration(bpm, beats, against, muted),
            origin = origin,
            sounds = preparedSounds,
            startDelayMillis = startDelayMillis,
            eventCapture = eventCapture
        )
    }

    fun polyrhythmConfiguration(
        bpm: Float,
        beats: Int,
        against: Int,
        muted: Boolean
    ): PlaybackInputResult<PolyrhythmConfiguration> =
        PlaybackInputBoundary.translate {
            createPolyrhythmConfiguration(bpm, beats, against, muted)
        }

    private fun createPolyrhythmConfiguration(
        bpm: Float,
        beats: Int,
        against: Int,
        muted: Boolean
    ): PolyrhythmConfiguration =
        PolyrhythmConfiguration(
            bpm = ExactTempo.fromFloat(bpm),
            beats = beats,
            against = against,
            muteMetronome = muted
        )

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
