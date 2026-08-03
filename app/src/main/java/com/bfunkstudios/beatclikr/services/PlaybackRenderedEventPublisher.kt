package com.bfunkstudios.beatclikr.services

internal class PlaybackRenderedEventPublisher(
    private val engine: PlaybackEnginePort,
    private val publish: (PlaybackCommittedEvent) -> Unit
) {
    private var nextCommittedSequence = 1L
    private var nextCaptureSequence = 0L

    fun firstEventScheduled(sessionId: PlaybackSessionId, intendedFrame: Long) {
        publish(
            PlaybackCommittedEvent.FirstEventScheduled(
                nextCommittedSequence++,
                sessionId,
                intendedFrame
            )
        )
    }

    fun drain(sessionId: PlaybackSessionId) {
        val batch = engine.drainRenderedEvents(nextCaptureSequence) ?: return
        nextCaptureSequence = batch.events.nextCaptureSequence
        if (batch.events.droppedRecords > 0) {
            publish(
                PlaybackCommittedEvent.RecordsDropped(
                    nextCommittedSequence++,
                    batch.events.droppedRecords
                )
            )
        }
        batch.events.records
            .filter { it.sessionId == sessionId.value }
            .forEach { record ->
                publish(
                    PlaybackCommittedEvent.Rendered(
                        nextCommittedSequence++,
                        sessionId,
                        record.eventSequence,
                        record.role,
                        record.intendedFrame,
                        record.muted,
                        presentationFor(record.intendedFrame, batch),
                        record.roleIndex
                    )
                )
            }
    }

    private fun presentationFor(
        intendedFrame: Long,
        batch: FrameAudioRenderedEventBatch
    ): EventPresentation {
        val correlation = batch.correlation ?: return EventPresentation.Unavailable
        return try {
            val frameDelta = Math.subtractExact(intendedFrame, correlation.presentedFrame)
            val wholeSeconds = Math.floorDiv(frameDelta, batch.sampleRate.toLong())
            val remainderFrames = Math.floorMod(frameDelta, batch.sampleRate.toLong())
            val deltaNanos = Math.addExact(
                Math.multiplyExact(wholeSeconds, NANOS_PER_SECOND),
                Math.multiplyExact(remainderFrames, NANOS_PER_SECOND) / batch.sampleRate
            )
            EventPresentation.Correlated(
                Math.addExact(correlation.presentationNanoTime, deltaNanos),
                correlation
            )
        } catch (_: ArithmeticException) {
            EventPresentation.Unavailable
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
