package com.bfunkstudios.beatclikr.music

enum class TimelineMode {
    STANDARD,
    POLYRHYTHM
}

interface FrameEventTimeline {
    val origin: SessionOrigin
    val mode: TimelineMode

    fun eventsIn(range: FrameRange): Sequence<FrameEvent>
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

data class DeadlineRecoveryState(
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
}

data class DeadlineRecoveryResult(
    val events: List<FrameEvent>,
    val state: DeadlineRecoveryState
)

object DeadlineRecovery {
    fun process(
        timeline: FrameEventTimeline,
        state: DeadlineRecoveryState,
        renderWindow: FrameRange
    ): DeadlineRecoveryResult {
        require(timeline.origin.sessionID == state.sessionID) {
            "Timeline session must match recovery state"
        }
        require(timeline.origin.originFrame == state.originFrame) {
            "Timeline origin must match recovery state"
        }
        require(timeline.mode == state.mode) { "Timeline mode must match recovery state" }

        val searchStart = maxOf(state.nextUnprocessedFrame, state.originFrame)
        if (renderWindow.endFrameExclusive <= searchStart) {
            return DeadlineRecoveryResult(emptyList(), state)
        }
        val expiredEnd = minOf(renderWindow.startFrame, renderWindow.endFrameExclusive)
        val expiredCount = if (expiredEnd > searchStart) {
            timeline.eventsIn(FrameRange(searchStart, expiredEnd)).countLong()
        } else {
            0
        }
        val futureStart = maxOf(searchStart, renderWindow.startFrame)
        val events = timeline.eventsIn(
            FrameRange(futureStart, renderWindow.endFrameExclusive)
        ).toList()
        require(events.zipWithNext().all { (first, second) ->
            first.intendedFrame < second.intendedFrame &&
                first.sequence.index < second.sequence.index
        }) { "Committed events must be strictly ordered and unique" }

        val diagnostics = state.diagnostics.copy(
            deadlineMisses = Math.addExact(state.diagnostics.deadlineMisses, expiredCount),
            droppedEvents = Math.addExact(state.diagnostics.droppedEvents, expiredCount),
            committedEvents = Math.addExact(state.diagnostics.committedEvents, events.size.toLong()),
            recoveryWindows = Math.addExact(
                state.diagnostics.recoveryWindows,
                if (expiredCount > 0) 1 else 0
            )
        )
        return DeadlineRecoveryResult(
            events = events,
            state = state.copy(
                nextUnprocessedFrame = renderWindow.endFrameExclusive,
                diagnostics = diagnostics
            )
        )
    }
}

private fun Sequence<FrameEvent>.countLong(): Long =
    fold(0L) { count, _ -> Math.incrementExact(count) }
