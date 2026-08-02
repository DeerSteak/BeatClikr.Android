package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.PracticeAccountingCheckpoint
import com.bfunkstudios.beatclikr.data.PracticeDayIdentity
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepository
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.di.ApplicationScope
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Singleton
class PracticeAccountingCoordinator @Inject constructor(
    private val lifecycle: PlaybackLifecycleObservation,
    private val repository: PracticeHistoryRepository,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private var collectionJob: Job? = null
    private var acknowledgedSequence = 0L
    private var active: ActivePeriod? = null

    @Synchronized
    fun start() {
        if (collectionJob != null) return
        collectionJob = scope.launch {
            recoverCheckpoint()
            lifecycle.lifecycleCheckpoint.collect { drainTransitions() }
        }
    }

    private suspend fun recoverCheckpoint() {
        val persisted = repository.getAccountingCheckpoint()
        val currentSequence = lifecycle.lifecycleCheckpoint.value.latestTransitionSequence
        acknowledgedSequence = persisted?.acknowledgedLifecycleSequence
            ?.takeIf { it <= currentSequence }
            ?: 0L
        if (persisted != null && persisted.activePlaybackSessionId != null) {
            repository.applyAccountingUpdate(
                null,
                null,
                0,
                0,
                idleCheckpoint(acknowledgedSequence)
            )
        }
    }

    private suspend fun drainTransitions() {
        val batch = lifecycle.lifecycleTransitionsAfter(acknowledgedSequence)
        check(batch.gap == null) { "Practice lifecycle journal gap: ${batch.gap}" }
        batch.transitions.forEach { transition ->
            applyTransition(transition)
            acknowledgedSequence = transition.sequence
            lifecycle.acknowledgeLifecycleTransitionsThrough(transition.sequence)
        }
    }

    private suspend fun applyTransition(transition: PlaybackStateTransition) {
        val nextPlaying = transition.to as? PlaybackTransportState.Playing
        val current = active
        when {
            nextPlaying != null && current?.sessionId == nextPlaying.context.sessionId ->
                checkpointActive(current, transition)
            nextPlaying != null -> {
                if (current != null) checkpointActive(current, transition, closing = true)
                beginPeriod(nextPlaying.context, transition)
            }
            current != null && transition.from is PlaybackTransportState.Playing ->
                checkpointActive(current, transition, closing = true)
            else -> persistAcknowledgement(transition.sequence)
        }
    }

    private suspend fun beginPeriod(
        context: PlaybackSessionContext,
        transition: PlaybackStateTransition
    ) {
        val period = ActivePeriod(
            context.sessionId,
            context.practiceItem,
            transition.occurredAtElapsedNanos
        )
        active = period
        repository.applyAccountingUpdate(
            transition.dayIdentity(),
            period.item,
            0,
            1,
            activeCheckpoint(transition.sequence, period)
        )
    }

    private suspend fun checkpointActive(
        period: ActivePeriod,
        transition: PlaybackStateTransition,
        closing: Boolean = false
    ) {
        val elapsed = (transition.occurredAtElapsedNanos - period.checkpointElapsedNanos)
            .coerceAtLeast(0)
        val updated = period.copy(checkpointElapsedNanos = transition.occurredAtElapsedNanos)
        active = if (closing) null else updated
        repository.applyAccountingUpdate(
            transition.dayIdentity(),
            period.item,
            elapsed,
            0,
            if (closing) idleCheckpoint(transition.sequence) else {
                activeCheckpoint(transition.sequence, updated)
            }
        )
    }

    private suspend fun persistAcknowledgement(sequence: Long) {
        repository.applyAccountingUpdate(null, null, 0, 0, idleCheckpoint(sequence))
    }

    private fun activeCheckpoint(
        sequence: Long,
        period: ActivePeriod
    ) = PracticeAccountingCheckpoint(
        acknowledgedLifecycleSequence = sequence,
        activePlaybackSessionId = period.sessionId.value,
        activeItemId = period.item.itemId,
        lastCheckpointElapsedNanos = period.checkpointElapsedNanos,
        periodCounted = true
    )

    private fun idleCheckpoint(sequence: Long) = PracticeAccountingCheckpoint(
        acknowledgedLifecycleSequence = sequence
    )

    private fun PlaybackStateTransition.dayIdentity() = PracticeDayIdentity.capture(
        Instant.ofEpochMilli(occurredAtWallMillis),
        ZoneId.of(timeZoneIdentifier)
    )

    private data class ActivePeriod(
        val sessionId: PlaybackSessionId,
        val item: PracticeItemSnapshot,
        val checkpointElapsedNanos: Long
    )
}
