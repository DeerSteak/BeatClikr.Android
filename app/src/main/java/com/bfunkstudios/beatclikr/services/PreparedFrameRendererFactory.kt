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
    private val startDelayMillis: Long = 0,
    private val maximumActiveVoices: Int = DEFAULT_MAXIMUM_ACTIVE_VOICES
) : PcmFrameRendererFactory {
    init {
        require(startDelayMillis >= 0) { "Start delay must not be negative" }
    }

    override fun create(
        properties: AudioBackendStreamProperties
    ): PublishedPcmFrameRenderer {
        var activeTimeline = StandardMetronomeTimeline(
            configuration,
            properties.sampleRate,
            delayedEventOrigin(properties)
        )
        return preparedPublication(
            activeTimeline,
            sounds,
            maximumActiveVoices,
            firstOutputFrame = origin.originFrame,
            standardUpdater = StandardFrameStreamUpdater { replacement, firstUnprocessedFrame ->
                val continuation = activeTimeline.continuationAtOrAfter(firstUnprocessedFrame)
                StandardMetronomeTimeline(
                    replacement,
                    properties.sampleRate,
                    origin.copy(originFrame = continuation.frame),
                    initialEventIndex = continuation.eventIndex
                ).also { activeTimeline = it }
            }
        )
    }

    private fun delayedEventOrigin(properties: AudioBackendStreamProperties): SessionOrigin =
        origin.copy(
            originFrame = delayedEventOriginFrame(
                origin.originFrame,
                startDelayMillis,
                properties.sampleRate
            )
        )
}

class PolyrhythmPreparedFrameRendererFactory(
    private val configuration: PolyrhythmConfiguration,
    private val origin: SessionOrigin,
    private val sounds: ActivePreparedSounds,
    private val startDelayMillis: Long = 0,
    private val maximumActiveVoices: Int = DEFAULT_MAXIMUM_ACTIVE_VOICES
) : PcmFrameRendererFactory {
    init {
        require(startDelayMillis >= 0) { "Start delay must not be negative" }
    }

    override fun create(
        properties: AudioBackendStreamProperties
    ): PublishedPcmFrameRenderer {
        var activeTimeline = PolyrhythmTimeline(
            configuration,
            properties.sampleRate,
            origin.copy(
                originFrame = delayedEventOriginFrame(
                    origin.originFrame,
                    startDelayMillis,
                    properties.sampleRate
                )
            )
        )
        return preparedPublication(
            activeTimeline,
            sounds,
            maximumActiveVoices,
            firstOutputFrame = origin.originFrame,
            polyrhythmUpdater = PolyrhythmFrameStreamUpdater {
                    replacement,
                    firstUnprocessedFrame ->
                val continuation = activeTimeline.nextCycleBoundaryAtOrAfter(
                    firstUnprocessedFrame
                )
                PolyrhythmTimeline(
                    replacement,
                    properties.sampleRate,
                    origin.copy(originFrame = continuation.frame),
                    initialEventIndex = continuation.eventIndex,
                    initialCycleIndex = continuation.cycleIndex
                ).also { activeTimeline = it }
            }
        )
    }
}

private fun preparedPublication(
    timeline: FrameEventTimeline,
    sounds: ActivePreparedSounds,
    maximumActiveVoices: Int,
    firstOutputFrame: Long,
    standardUpdater: StandardFrameStreamUpdater? = null,
    polyrhythmUpdater: PolyrhythmFrameStreamUpdater? = null
): PublishedPcmFrameRenderer {
    val renderer = FramePcmRenderer(
        timeline,
        RenderWaveforms(sounds.beat, sounds.rhythm),
        maximumActiveVoices
    )
    return PublishedPcmFrameRenderer(
        renderer,
        TimelineFrameStreamRecovery(timeline),
        firstOutputFrame = firstOutputFrame,
        standardUpdater = standardUpdater,
        polyrhythmUpdater = polyrhythmUpdater
    )
}

private fun delayedEventOriginFrame(
    firstOutputFrame: Long,
    startDelayMillis: Long,
    obtainedSampleRate: Int
): Long = Math.addExact(
    firstOutputFrame,
    Math.multiplyExact(startDelayMillis, obtainedSampleRate.toLong()) / 1_000
)

private const val DEFAULT_MAXIMUM_ACTIVE_VOICES = 16
