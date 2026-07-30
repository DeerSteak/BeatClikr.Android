package com.bfunkstudios.beatclikr.music

data class FrameRange(
    val startFrame: Long,
    val endFrameExclusive: Long
) {
    init {
        require(startFrame >= 0) { "Start frame must not be negative" }
        require(endFrameExclusive >= startFrame) { "End frame must not precede start frame" }
    }
}

class AbsoluteAudioTimeline(
    sampleRate: Int,
    intervalsPerMinute: ExactFraction
) {
    val framesPerInterval: ExactFraction
    private val renderPeriod: RenderFramePeriod

    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(intervalsPerMinute > ExactFraction.of(0)) { "Intervals per minute must be positive" }
        framesPerInterval =
            ExactFraction.of(sampleRate.toLong()) * ExactFraction.of(60) / intervalsPerMinute
        require(framesPerInterval >= ExactFraction.of(1)) {
            "Each interval must occupy at least one frame"
        }
        renderPeriod = RenderFramePeriod(framesPerInterval)
    }

    fun framePosition(intervalIndex: Long): Long {
        require(intervalIndex >= 0) { "Interval index must not be negative" }
        return renderPeriod.framePosition(intervalIndex)
    }

    fun firstIntervalAtOrAfter(frame: Long): Long {
        require(frame >= 0) { "Frame must not be negative" }
        var candidate = renderPeriod.estimateInterval(frame)
        while (candidate > 0 && framePosition(candidate - 1) >= frame) candidate--
        while (framePosition(candidate) < frame) {
            require(candidate < Long.MAX_VALUE) { "Interval index exhausted" }
            candidate++
        }
        return candidate
    }
}

private class RenderFramePeriod(period: ExactFraction) {
    private val numerator = period.numerator.longValueExact()
    private val denominator = period.denominator.longValueExact()
    private val wholeFrames = numerator / denominator
    private val remainder = numerator % denominator

    fun estimateInterval(frame: Long): Long = (frame / wholeFrames).coerceAtLeast(0)

    fun framePosition(intervalIndex: Long): Long {
        val completeRemainderCycles = intervalIndex / denominator
        val partialCycleIndex = intervalIndex % denominator
        val partialProduct = Math.multiplyExact(partialCycleIndex, remainder)
        val partialFrames = partialProduct / denominator
        val partialRemainder = partialProduct % denominator
        val roundingThreshold = denominator / 2 + denominator % 2
        val roundedPartial = partialFrames + if (partialRemainder >= roundingThreshold) 1 else 0
        return Math.addExact(
            Math.addExact(
                Math.multiplyExact(intervalIndex, wholeFrames),
                Math.multiplyExact(completeRemainderCycles, remainder)
            ),
            roundedPartial
        )
    }
}

class StandardMetronomeTimeline(
    val configuration: StandardMetronomeConfiguration,
    val sampleRate: Int,
    override val origin: SessionOrigin,
    private val initialEventIndex: Long = 0
) : FrameEventTimeline {
    override val mode = TimelineMode.STANDARD
    private val patternSize: Int
    private val subdivisions: Int
    private val timeline: AbsoluteAudioTimeline

    init {
        when (val timing = configuration.timing) {
            is StandardTiming.Regular -> {
                patternSize = timing.subdivision.subdivisions
                subdivisions = timing.subdivision.subdivisions
            }
            is StandardTiming.Additive -> {
                patternSize = timing.accents.size
                subdivisions = timing.stepUnit.subdivisions
            }
        }
        require(initialEventIndex >= 0) { "Initial event index must not be negative" }
        val intervalsPerMinute =
            configuration.bpm.beatsPerMinute * ExactFraction.of(subdivisions.toLong())
        timeline = AbsoluteAudioTimeline(sampleRate, intervalsPerMinute)
    }

    fun continuationAtOrAfter(frame: Long): StandardTimelineContinuation {
        if (frame <= origin.originFrame) {
            return StandardTimelineContinuation(origin.originFrame, initialEventIndex)
        }
        val intervalIndex = timeline.firstIntervalAtOrAfter(frame - origin.originFrame)
        return StandardTimelineContinuation(
            frame = Math.addExact(origin.originFrame, timeline.framePosition(intervalIndex)),
            eventIndex = eventIndex(intervalIndex)
        )
    }

    override fun eventsIn(range: FrameRange): Sequence<FrameEvent> = sequence {
        if (range.endFrameExclusive <= origin.originFrame) return@sequence
        val relativeStart = (range.startFrame - origin.originFrame).coerceAtLeast(0)
        var intervalIndex = timeline.firstIntervalAtOrAfter(relativeStart)
        while (true) {
            val intendedFrame = Math.addExact(
                origin.originFrame,
                timeline.framePosition(intervalIndex)
            )
            if (intendedFrame >= range.endFrameExclusive) break
            if (intendedFrame >= range.startFrame) {
                yield(eventAt(intervalIndex, intendedFrame))
            }
            require(intervalIndex < Long.MAX_VALUE) { "Interval index exhausted" }
            intervalIndex++
        }
    }

    override fun eventCountIn(range: FrameRange): Long {
        if (range.endFrameExclusive <= origin.originFrame) return 0
        val relativeStart = (range.startFrame - origin.originFrame).coerceAtLeast(0)
        val relativeEnd = range.endFrameExclusive - origin.originFrame
        val first = timeline.firstIntervalAtOrAfter(relativeStart)
        val end = timeline.firstIntervalAtOrAfter(relativeEnd)
        return Math.subtractExact(end, first)
    }

    override fun visitEvents(
        startFrame: Long,
        endFrameExclusive: Long,
        consumer: FrameRangeEventConsumer
    ): Boolean {
        if (endFrameExclusive <= origin.originFrame) return true
        val relativeStart = (startFrame - origin.originFrame).coerceAtLeast(0)
        var intervalIndex = timeline.firstIntervalAtOrAfter(relativeStart)
        while (true) {
            val intendedFrame = Math.addExact(origin.originFrame, timeline.framePosition(intervalIndex))
            if (intendedFrame >= endFrameExclusive) return true
            if (intendedFrame >= startFrame) {
                val index = patternIndex(intervalIndex)
                val isBeat = isBeat(index)
                val role = if (usesBeatSound(index, isBeat)) SoundRole.BEAT else SoundRole.RHYTHM
                if (!consumer.accept(
                        origin.sessionID.value,
                        eventIndex(intervalIndex),
                        intendedFrame,
                        MusicalEventRole.STANDARD,
                        role,
                        null,
                        null,
                        configuration.muteMetronome,
                        roleIndices = packRoleIndices(index)
                    )
                ) {
                    return true
                }
            }
            intervalIndex++
        }
    }

    private fun eventAt(intervalIndex: Long, intendedFrame: Long): FrameEvent {
        val eventIndex = eventIndex(intervalIndex)
        val index = patternIndex(intervalIndex)
        val isBeat = isBeat(index)
        val usesBeatSound = usesBeatSound(index, isBeat)
        return FrameEvent(
            sequence = EventSequence(origin.sessionID, eventIndex),
            intendedFrame = intendedFrame,
            primary = EventVoice(
                role = MusicalEventRole.STANDARD,
                soundRole = if (usesBeatSound) SoundRole.BEAT else SoundRole.RHYTHM,
                beatIdentity = beatIdentity(index, isBeat),
                position = CyclePosition(
                    cycleIndex = eventIndex / patternSize,
                    index = index
                )
            ),
            muteMetronome = configuration.muteMetronome
        )
    }

    private fun eventIndex(intervalIndex: Long): Long =
        Math.addExact(initialEventIndex, intervalIndex)

    private fun patternIndex(intervalIndex: Long): Int =
        (eventIndex(intervalIndex) % patternSize).toInt()

    private fun isBeat(index: Int): Boolean =
        when (val timing = configuration.timing) {
            is StandardTiming.Regular -> index == 0
            is StandardTiming.Additive -> timing.accents[index]
        }

    private fun usesBeatSound(index: Int, isBeat: Boolean): Boolean =
        when (configuration.timing) {
            is StandardTiming.Additive -> isBeat
            is StandardTiming.Regular ->
                index == 0 ||
                    configuration.alternateSixteenth &&
                    subdivisions == StandardSubdivision.SIXTEENTH.subdivisions &&
                    index % 2 == 0
        }

    private fun beatIdentity(index: Int, isBeat: Boolean): BeatIdentity =
        when (configuration.timing) {
            is StandardTiming.Additive ->
                if (isBeat) BeatIdentity.ACCENT else BeatIdentity.SUBDIVISION
            is StandardTiming.Regular ->
                if (index == 0) BeatIdentity.BEAT else BeatIdentity.SUBDIVISION
        }
}

data class StandardTimelineContinuation(
    val frame: Long,
    val eventIndex: Long
)
