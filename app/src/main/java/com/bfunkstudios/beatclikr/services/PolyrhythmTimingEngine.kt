package com.bfunkstudios.beatclikr.services

import android.os.Handler
import android.os.SystemClock
import com.bfunkstudios.beatclikr.data.PolyrhythmGrid

internal class PolyrhythmTimingEngine(
    private val handler: Handler,
    private val outputLatencyNanos: () -> Long,
    private val firstBeatDelayMs: Long,
    private val lookaheadToleranceMs: Long
) {
    var delegate: PolyrhythmAudioEngineDelegate? = null

    val isRunning: Boolean
        get() = isPlaying

    private var isPlaying = false

    private var bpm = 120f
    private var against = 2
    private var beats = 3
    private var grid = PolyrhythmGrid.create(beats = 3, against = 2)
    private var stepDurationNanos = 0L
    private var stepIndex = 0
    private var nextStepTimeNanos = 0L
    private var pendingBpm = 0f
    private var pendingBeats = 0
    private var pendingAgainst = 0
    private var hasPendingUpdate = false

    fun start(bpm: Float, beats: Int, against: Int) {
        handler.removeCallbacks(runnable)
        doStart(bpm, beats, against)
    }

    fun updateAtCycleBoundary(bpm: Float, beats: Int, against: Int) {
        pendingBpm = bpm
        pendingBeats = beats
        pendingAgainst = against
        hasPendingUpdate = true
        if (stepIndex == 0) applyPendingUpdate()
    }

    fun stop() {
        isPlaying = false
        handler.removeCallbacks(runnable)
        stepIndex = 0
        hasPendingUpdate = false
    }

    private fun doStart(bpm: Float, beats: Int, against: Int) {
        this.bpm = bpm
        this.against = against.coerceIn(1, 15)
        this.beats = beats.coerceIn(1, 15)
        this.grid = PolyrhythmGrid.create(beats = this.beats, against = this.against)
        // Compute directly in nanoseconds to minimize floating-point precision loss
        val nanosPerBeat = 60_000_000_000.0 / this.bpm
        stepDurationNanos = (this.against * nanosPerBeat / grid.lcm).toLong()
        stepIndex = 0
        nextStepTimeNanos = SystemClock.elapsedRealtimeNanos() + (firstBeatDelayMs * 1_000_000L)
        isPlaying = true
        handler.removeCallbacks(runnable)
        scheduleNextStep()
    }

    private val runnable = object : Runnable {
        override fun run() {
            playScheduledStep()
            if (isPlaying) scheduleNextStep()
        }
    }

    private fun playScheduledStep() {
        if (!isPlaying) {
            handler.removeCallbacks(runnable)
            return
        }

        dropExpiredVisualSteps()
        playCurrentStep(nextStepTimeNanos)
        nextStepTimeNanos += stepDurationNanos
        stepIndex = (stepIndex + 1) % grid.lcm
        if (stepIndex == 0) applyPendingUpdate()
    }

    private fun dropExpiredVisualSteps() {
        while (true) {
            val expiredSteps = expiredEventCount(
                SystemClock.elapsedRealtimeNanos(),
                nextStepTimeNanos,
                lookaheadToleranceMs * NANOS_PER_MILLISECOND,
                stepDurationNanos
            )
            if (expiredSteps == 0L) return
            val stepsToPendingBoundary = if (hasPendingUpdate && stepIndex != 0) {
                grid.lcm - stepIndex
            } else {
                0
            }
            if (stepsToPendingBoundary > 0 && expiredSteps >= stepsToPendingBoundary) {
                advanceVisualSteps(stepsToPendingBoundary.toLong())
                applyPendingUpdate()
            } else {
                advanceVisualSteps(expiredSteps)
                return
            }
        }
    }

    private fun advanceVisualSteps(stepCount: Long) {
        nextStepTimeNanos = Math.addExact(
            nextStepTimeNanos,
            Math.multiplyExact(stepCount, stepDurationNanos)
        )
        stepIndex = ((stepIndex + stepCount % grid.lcm).toInt()) % grid.lcm
    }

    private fun scheduleNextStep() {
        val triggerNanos = nextStepTimeNanos -
            lookaheadToleranceMs * NANOS_PER_MILLISECOND
        val remaining = triggerNanos - SystemClock.elapsedRealtimeNanos()
        val delayMillis = if (remaining <= 0) {
            0
        } else {
            (remaining + NANOS_PER_MILLISECOND - 1) / NANOS_PER_MILLISECOND
        }
        handler.postDelayed(runnable, delayMillis)
    }

    private fun applyPendingUpdate() {
        if (!hasPendingUpdate) return
        bpm = pendingBpm
        beats = pendingBeats.coerceIn(1, 15)
        against = pendingAgainst.coerceIn(1, 15)
        grid = PolyrhythmGrid.create(beats = beats, against = against)
        val nanosPerBeat = 60_000_000_000.0 / bpm
        stepDurationNanos = (against * nanosPerBeat / grid.lcm).toLong()
        hasPendingUpdate = false
    }

    private fun playCurrentStep(scheduledTimeNanos: Long) {
        val step = grid.stepAt(stepIndex)
        val beatFired = step.beatFired
        val rhythmFired = step.rhythmFired
        if (!beatFired && !rhythmFired) return

        val visualStepTimeNanos = scheduledTimeNanos + outputLatencyNanos()
        delegate?.polyrhythmBeatFired(
            beatFired = beatFired,
            rhythmFired = rhythmFired,
            beatIndex = step.beatIndex,
            rhythmIndex = step.rhythmIndex,
            stepTimeNanos = visualStepTimeNanos,
            beatDurationNanos = (60_000_000_000.0 / bpm).toLong().coerceAtLeast(1L),
            rhythmDurationNanos = (against * (60_000_000_000.0 / bpm) / beats).toLong().coerceAtLeast(1L)
        )
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
