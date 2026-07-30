package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.music.FrameEventTimeline
import com.bfunkstudios.beatclikr.music.PolyrhythmConfiguration
import com.bfunkstudios.beatclikr.music.PolyrhythmTimeline
import com.bfunkstudios.beatclikr.music.SessionOrigin
import com.bfunkstudios.beatclikr.music.StandardMetronomeConfiguration
import com.bfunkstudios.beatclikr.music.StandardMetronomeTimeline

class StandardPreparedFrameRendererFactory(
    private val configuration: StandardMetronomeConfiguration,
    private val origin: SessionOrigin,
    private val sounds: ActivePreparedSounds,
    private val maximumActiveVoices: Int = DEFAULT_MAXIMUM_ACTIVE_VOICES
) : PcmFrameRendererFactory {
    override fun create(
        properties: AudioBackendStreamProperties
    ): PublishedPcmFrameRenderer = publish(
        StandardMetronomeTimeline(
            configuration,
            properties.sampleRate,
            origin
        )
    )

    private fun publish(timeline: FrameEventTimeline): PublishedPcmFrameRenderer =
        preparedPublication(timeline, sounds, maximumActiveVoices)
}

class PolyrhythmPreparedFrameRendererFactory(
    private val configuration: PolyrhythmConfiguration,
    private val origin: SessionOrigin,
    private val sounds: ActivePreparedSounds,
    private val maximumActiveVoices: Int = DEFAULT_MAXIMUM_ACTIVE_VOICES
) : PcmFrameRendererFactory {
    override fun create(
        properties: AudioBackendStreamProperties
    ): PublishedPcmFrameRenderer = preparedPublication(
        PolyrhythmTimeline(
            configuration,
            properties.sampleRate,
            origin
        ),
        sounds,
        maximumActiveVoices
    )
}

private fun preparedPublication(
    timeline: FrameEventTimeline,
    sounds: ActivePreparedSounds,
    maximumActiveVoices: Int
): PublishedPcmFrameRenderer {
    val renderer = FramePcmRenderer(
        timeline,
        RenderWaveforms(sounds.beat, sounds.rhythm),
        maximumActiveVoices
    )
    return PublishedPcmFrameRenderer(
        renderer,
        TimelineFrameStreamRecovery(timeline)
    )
}

private const val DEFAULT_MAXIMUM_ACTIVE_VOICES = 16
