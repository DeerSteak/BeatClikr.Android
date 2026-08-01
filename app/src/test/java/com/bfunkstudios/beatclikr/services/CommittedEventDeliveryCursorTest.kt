package com.bfunkstudios.beatclikr.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.channels.BufferOverflow
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommittedEventDeliveryCursorTest {
    @Test
    fun slowSubscriberDetectsOverflowBeyondBoundedCapacity() = runTest {
        val flow = MutableSharedFlow<PlaybackCommittedEvent>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val release = CompletableDeferred<Unit>()
        val results = mutableListOf<CommittedEventDeliveryResult>()
        val observedSequences = mutableListOf<Long>()
        val cursor = CommittedEventDeliveryCursor()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { event ->
                observedSequences += event.sequence
                results += cursor.accept(event)
                if (event.sequence == 1L) release.await()
            }
        }

        flow.tryEmit(scheduled(1))
        (2L..12L).forEach { flow.tryEmit(scheduled(it)) }
        release.complete(Unit)
        runCurrent()
        collector.cancelAndJoin()

        val gap = results.filterIsInstance<CommittedEventDeliveryResult.Gap>().single()
        assertEquals(listOf(1L, 9L, 10L, 11L, 12L), observedSequences)
        assertEquals(7L, gap.detail.missingCount)
        assertEquals(2L, gap.detail.expectedSequence)
        assertEquals(9L, gap.detail.observedSequence)
    }

    @Test
    fun duplicateIsIgnoredWithoutReportingLoss() {
        val cursor = CommittedEventDeliveryCursor()

        assertEquals(CommittedEventDeliveryResult.Accepted, cursor.accept(scheduled(1)))
        assertEquals(CommittedEventDeliveryResult.Duplicate, cursor.accept(scheduled(1)))
    }

    private fun scheduled(sequence: Long) = PlaybackCommittedEvent.FirstEventScheduled(
        sequence,
        PlaybackSessionId(1),
        0
    )
}
