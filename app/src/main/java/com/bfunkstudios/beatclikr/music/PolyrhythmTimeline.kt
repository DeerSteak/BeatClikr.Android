package com.bfunkstudios.beatclikr.music

class PolyrhythmTimeline(
    val configuration: PolyrhythmConfiguration,
    val sampleRate: Int,
    override val origin: SessionOrigin,
    private val initialEventIndex: Long = 0,
    private val initialCycleIndex: Long = 0
) : FrameEventTimeline {
    override val mode = TimelineMode.POLYRHYTHM
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

    init {
        require(initialEventIndex >= 0) { "Initial event index must not be negative" }
        require(initialCycleIndex >= 0) { "Initial cycle index must not be negative" }
    }

    fun nextCycleBoundaryAtOrAfter(frame: Long): PolyrhythmTimelineContinuation {
        if (frame <= origin.originFrame) {
            return PolyrhythmTimelineContinuation(
                origin.originFrame,
                initialEventIndex,
                initialCycleIndex
            )
        }
        val firstSlot = timeline.firstIntervalAtOrAfter(frame - origin.originFrame)
        val cycleSlot = if (firstSlot % slotsPerCycle == 0L) {
            firstSlot
        } else {
            Math.multiplyExact(firstSlot / slotsPerCycle + 1, slotsPerCycle.toLong())
        }
        return PolyrhythmTimelineContinuation(
            frame = Math.addExact(origin.originFrame, timeline.framePosition(cycleSlot)),
            eventIndex = Math.addExact(initialEventIndex, eventCountBefore(cycleSlot)),
            cycleIndex = Math.addExact(initialCycleIndex, cycleSlot / slotsPerCycle)
        )
    }

    override fun eventsIn(range: FrameRange): Sequence<FrameEvent> = sequence {
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

    override fun eventCountIn(range: FrameRange): Long {
        if (range.endFrameExclusive <= origin.originFrame) return 0
        val relativeStart = (range.startFrame - origin.originFrame).coerceAtLeast(0)
        val relativeEnd = range.endFrameExclusive - origin.originFrame
        val firstSlot = timeline.firstIntervalAtOrAfter(relativeStart)
        val endSlot = timeline.firstIntervalAtOrAfter(relativeEnd)
        return Math.subtractExact(eventCountBefore(endSlot), eventCountBefore(firstSlot))
    }

    override fun visitEvents(
        startFrame: Long,
        endFrameExclusive: Long,
        consumer: FrameRangeEventConsumer
    ): Boolean {
        if (endFrameExclusive <= origin.originFrame) return true
        val relativeStart = (startFrame - origin.originFrame).coerceAtLeast(0)
        var slotIndex = timeline.firstIntervalAtOrAfter(relativeStart)
        while (true) {
            val intendedFrame = Math.addExact(origin.originFrame, timeline.framePosition(slotIndex))
            if (intendedFrame >= endFrameExclusive) return true
            val slot = (slotIndex % slotsPerCycle).toInt()
            if (intendedFrame >= startFrame && hasEventAt(slot)) {
                val primary = primarySoundAt(slot)
                val secondary = secondarySoundAt(slot)
                val eventIndex = eventIndex(slotIndex, slot)
                val primaryIndex = if (primary == SoundRole.BEAT) {
                    slot / beatSlotInterval
                } else {
                    slot / rhythmSlotInterval
                }
                val secondaryIndex =
                    if (secondary == SoundRole.RHYTHM) slot / rhythmSlotInterval else 0
                if (!consumer.accept(
                        origin.sessionID.value,
                        eventIndex,
                        intendedFrame,
                        if (primary == SoundRole.BEAT) {
                            MusicalEventRole.POLYRHYTHM_BEAT
                        } else {
                            MusicalEventRole.POLYRHYTHM_RHYTHM
                        },
                        primary,
                        if (secondary == SoundRole.RHYTHM) {
                            MusicalEventRole.POLYRHYTHM_RHYTHM
                        } else {
                            null
                        },
                        secondary,
                        configuration.muteMetronome,
                        roleIndices = packRoleIndices(primaryIndex, secondaryIndex)
                    )
                ) {
                    return true
                }
            }
            slotIndex++
        }
    }

    private fun eventAt(slotIndex: Long, slot: Int, intendedFrame: Long): FrameEvent {
        val localCycleIndex = slotIndex / slotsPerCycle
        val cycleIndex = Math.addExact(initialCycleIndex, localCycleIndex)
        val beatFired = beatFiresAt(slot)
        val rhythmFired = rhythmFiresAt(slot)
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
        val eventIndex = eventIndex(slotIndex, slot)
        return FrameEvent(
            sequence = EventSequence(origin.sessionID, eventIndex),
            intendedFrame = intendedFrame,
            primary = requireNotNull(beatVoice ?: rhythmVoice),
            secondary = if (beatVoice != null) rhythmVoice else null,
            muteMetronome = configuration.muteMetronome
        )
    }

    private fun eventIndex(slotIndex: Long, slot: Int): Long {
        val localCycleIndex = slotIndex / slotsPerCycle
        val localEventIndex = Math.addExact(
            Math.multiplyExact(localCycleIndex, eventSlots.size.toLong()),
            eventSlots.binarySearch(slot).toLong()
        )
        return Math.addExact(initialEventIndex, localEventIndex)
    }

    private fun hasEventAt(slot: Int): Boolean =
        beatFiresAt(slot) || rhythmFiresAt(slot)

    private fun beatFiresAt(slot: Int): Boolean = slot % beatSlotInterval == 0

    private fun rhythmFiresAt(slot: Int): Boolean = slot % rhythmSlotInterval == 0

    private fun primarySoundAt(slot: Int): SoundRole =
        if (beatFiresAt(slot)) SoundRole.BEAT else SoundRole.RHYTHM

    private fun secondarySoundAt(slot: Int): SoundRole? =
        if (beatFiresAt(slot) && rhythmFiresAt(slot)) SoundRole.RHYTHM else null

    private fun eventCountBefore(slotIndex: Long): Long {
        val completeCycles = slotIndex / slotsPerCycle
        val slotInCycle = (slotIndex % slotsPerCycle).toInt()
        val insertion = eventSlots.binarySearch(slotInCycle)
        val partialCount = if (insertion >= 0) insertion else -insertion - 1
        return Math.addExact(
            Math.multiplyExact(completeCycles, eventSlots.size.toLong()),
            partialCount.toLong()
        )
    }

    private fun leastCommonMultiple(first: Int, second: Int): Int =
        first / greatestCommonDivisor(first, second) * second

    private tailrec fun greatestCommonDivisor(first: Int, second: Int): Int =
        if (second == 0) first else greatestCommonDivisor(second, first % second)
}

data class PolyrhythmTimelineContinuation(
    val frame: Long,
    val eventIndex: Long,
    val cycleIndex: Long
)
