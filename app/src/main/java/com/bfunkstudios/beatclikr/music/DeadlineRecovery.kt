package com.bfunkstudios.beatclikr.music

enum class TimelineMode {
    STANDARD,
    POLYRHYTHM
}

fun interface FrameRangeEventConsumer {
    fun accept(
        sessionId: Long,
        eventSequence: Long,
        intendedFrame: Long,
        primaryRole: MusicalEventRole,
        primarySound: SoundRole,
        secondaryRole: MusicalEventRole?,
        secondarySound: SoundRole?,
        muted: Boolean
    ): Boolean
}

interface FrameRangeEventSource {
    fun visitEvents(
        startFrame: Long,
        endFrameExclusive: Long,
        consumer: FrameRangeEventConsumer
    ): Boolean
}

interface FrameEventTimeline : FrameRangeEventSource {
    val origin: SessionOrigin
    val mode: TimelineMode

    fun eventsIn(range: FrameRange): Sequence<FrameEvent>
    fun eventCountIn(range: FrameRange): Long
}

data class DeadlineDiagnostics(
    val sessionID: SessionID,
    val mode: TimelineMode,
    val deadlineMisses: Long = 0,
    val droppedEvents: Long = 0,
    val committedEvents: Long = 0,
    val recoveryWindows: Long = 0
) {
    init {
        require(deadlineMisses >= 0) { "Deadline misses must not be negative" }
        require(droppedEvents >= 0) { "Dropped events must not be negative" }
        require(committedEvents >= 0) { "Committed events must not be negative" }
        require(recoveryWindows >= 0) { "Recovery windows must not be negative" }
    }
}

class DeadlineRecoveryState private constructor(
    val sessionID: SessionID,
    val mode: TimelineMode,
    val originFrame: Long,
    val nextUnprocessedFrame: Long,
    val diagnostics: DeadlineDiagnostics
) {
    init {
        require(originFrame >= 0) { "Origin frame must not be negative" }
        require(nextUnprocessedFrame >= originFrame) {
            "Next unprocessed frame must not precede the origin"
        }
        require(diagnostics.sessionID == sessionID) { "Diagnostic session must match recovery state" }
        require(diagnostics.mode == mode) { "Diagnostic mode must match recovery state" }
    }

    companion object {
        fun atOrigin(timeline: FrameEventTimeline): DeadlineRecoveryState =
            DeadlineRecoveryState(
                sessionID = timeline.origin.sessionID,
                mode = timeline.mode,
                originFrame = timeline.origin.originFrame,
                nextUnprocessedFrame = timeline.origin.originFrame,
                diagnostics = DeadlineDiagnostics(
                    sessionID = timeline.origin.sessionID,
                    mode = timeline.mode
                )
            )
    }

    internal fun advanced(
        nextUnprocessedFrame: Long,
        diagnostics: DeadlineDiagnostics
    ): DeadlineRecoveryState =
        DeadlineRecoveryState(
            sessionID = sessionID,
            mode = mode,
            originFrame = originFrame,
            nextUnprocessedFrame = nextUnprocessedFrame,
            diagnostics = diagnostics
        )

    fun synchronizedTo(nextUnprocessedFrame: Long): DeadlineRecoveryState {
        require(nextUnprocessedFrame >= this.nextUnprocessedFrame) {
            "Recovery state cannot move backward"
        }
        return advanced(nextUnprocessedFrame, diagnostics)
    }
}

data class DeadlineRecoveryResult(
    val events: List<FrameEvent>,
    val state: DeadlineRecoveryState
)

object DeadlineRecovery {
    fun recoverTo(
        timeline: FrameEventTimeline,
        state: DeadlineRecoveryState,
        nextRenderFrame: Long
    ): DeadlineRecoveryState {
        validate(timeline, state)
        require(nextRenderFrame >= state.nextUnprocessedFrame) {
            "Recovery frame cannot precede unprocessed output"
        }
        val dropped = if (nextRenderFrame > state.nextUnprocessedFrame) {
            timeline.eventCountIn(
                FrameRange(state.nextUnprocessedFrame, nextRenderFrame)
            )
        } else {
            0
        }
        return state.advanced(
            nextUnprocessedFrame = nextRenderFrame,
            diagnostics = state.diagnostics.copy(
                deadlineMisses = Math.addExact(state.diagnostics.deadlineMisses, dropped),
                droppedEvents = Math.addExact(state.diagnostics.droppedEvents, dropped),
                recoveryWindows = Math.addExact(
                    state.diagnostics.recoveryWindows,
                    if (dropped > 0) 1 else 0
                )
            )
        )
    }

    fun process(
        timeline: FrameEventTimeline,
        state: DeadlineRecoveryState,
        renderWindow: FrameRange
    ): DeadlineRecoveryResult {
        validate(timeline, state)
        val searchStart = maxOf(state.nextUnprocessedFrame, state.originFrame)
        if (renderWindow.endFrameExclusive <= searchStart) {
            return DeadlineRecoveryResult(emptyList(), state)
        }
        val expiredState = recoverTo(
            timeline,
            state,
            minOf(renderWindow.startFrame, renderWindow.endFrameExclusive)
                .coerceAtLeast(searchStart)
        )
        val futureStart = maxOf(searchStart, renderWindow.startFrame)
        val events = timeline.eventsIn(
            FrameRange(futureStart, renderWindow.endFrameExclusive)
        ).toList()

        val diagnostics = expiredState.diagnostics.copy(
            committedEvents = Math.addExact(
                expiredState.diagnostics.committedEvents,
                events.size.toLong()
            )
        )
        return DeadlineRecoveryResult(
            events = events,
            state = expiredState.advanced(renderWindow.endFrameExclusive, diagnostics)
        )
    }

    private fun validate(
        timeline: FrameEventTimeline,
        state: DeadlineRecoveryState
    ) {
        require(timeline.origin.sessionID == state.sessionID) {
            "Timeline session must match recovery state"
        }
        require(timeline.origin.originFrame == state.originFrame) {
            "Timeline origin must match recovery state"
        }
        require(timeline.mode == state.mode) { "Timeline mode must match recovery state" }
    }
}
