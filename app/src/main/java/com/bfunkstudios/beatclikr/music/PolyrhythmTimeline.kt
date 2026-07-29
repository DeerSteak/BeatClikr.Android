package com.bfunkstudios.beatclikr.music

class PolyrhythmTimeline(
    val configuration: PolyrhythmConfiguration,
    val sampleRate: Int,
    val origin: SessionOrigin
) {
    private val slotsPerCycle = leastCommonMultiple(configuration.beats, configuration.against)
    private val beatSlotInterval = slotsPerCycle / configuration.against
    private val rhythmSlotInterval = slotsPerCycle / configuration.beats
    private val eventSlots = (0 until slotsPerCycle).filter(::hasEventAt)
    private val timeline = AbsoluteAudioTimeline(
        sampleRate = sampleRate,
        intervalsPerMinute = configuration.bpm.beatsPerMinute *
            ExactFraction.of(slotsPerCycle.toLong()) /
            ExactFraction.of(configuration.against.toLong())
    )

    fun eventsIn(range: FrameRange): Sequence<FrameEvent> = sequence {
        if (range.endFrameExclusive <= origin.originFrame) return@sequence
        val relativeStart = (range.startFrame - origin.originFrame).coerceAtLeast(0)
        var slotIndex = timeline.firstIntervalAtOrAfter(relativeStart)
        while (true) {
            val intendedFrame = Math.addExact(origin.originFrame, timeline.framePosition(slotIndex))
            if (intendedFrame >= range.endFrameExclusive) break
            val slot = (slotIndex % slotsPerCycle).toInt()
            if (intendedFrame >= range.startFrame && hasEventAt(slot)) {
                yield(eventAt(slotIndex, slot, intendedFrame))
            }
            require(slotIndex < Long.MAX_VALUE) { "Polyrhythm slot index exhausted" }
            slotIndex++
        }
    }

    private fun eventAt(slotIndex: Long, slot: Int, intendedFrame: Long): FrameEvent {
        val cycleIndex = slotIndex / slotsPerCycle
        val beatFired = slot % beatSlotInterval == 0
        val rhythmFired = slot % rhythmSlotInterval == 0
        val beatVoice = if (beatFired) {
            EventVoice(
                role = MusicalEventRole.POLYRHYTHM_BEAT,
                soundRole = SoundRole.BEAT,
                beatIdentity = BeatIdentity.BEAT,
                position = CyclePosition(cycleIndex, slot / beatSlotInterval)
            )
        } else {
            null
        }
        val rhythmVoice = if (rhythmFired) {
            EventVoice(
                role = MusicalEventRole.POLYRHYTHM_RHYTHM,
                soundRole = SoundRole.RHYTHM,
                beatIdentity = BeatIdentity.BEAT,
                position = CyclePosition(cycleIndex, slot / rhythmSlotInterval)
            )
        } else {
            null
        }
        val eventIndex = Math.addExact(
            Math.multiplyExact(cycleIndex, eventSlots.size.toLong()),
            eventSlots.binarySearch(slot).toLong()
        )
        return FrameEvent(
            sequence = EventSequence(origin.sessionID, eventIndex),
            intendedFrame = intendedFrame,
            primary = requireNotNull(beatVoice ?: rhythmVoice),
            secondary = if (beatVoice != null) rhythmVoice else null,
            muteMetronome = configuration.muteMetronome
        )
    }

    private fun hasEventAt(slot: Int): Boolean =
        slot % beatSlotInterval == 0 || slot % rhythmSlotInterval == 0

    private fun leastCommonMultiple(first: Int, second: Int): Int =
        first / greatestCommonDivisor(first, second) * second

    private tailrec fun greatestCommonDivisor(first: Int, second: Int): Int =
        if (second == 0) first else greatestCommonDivisor(second, first % second)
}
