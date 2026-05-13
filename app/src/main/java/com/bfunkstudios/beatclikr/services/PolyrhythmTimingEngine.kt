package com.bfunkstudios.beatclikr.services

import android.os.Handler
import android.os.SystemClock
import com.bfunkstudios.beatclikr.data.PolyrhythmGrid

internal class PolyrhythmTimingEngine(
    private val handler: Handler,
    private val isMuted: () -> Boolean,
    private val isLoaded: () -> Boolean,
    private val playBeatSound: () -> Unit,
    private val playRhythmSound: () -> Unit,
    private val playBeatAndRhythmSounds: () -> Unit,
    private val outputLatencyNanos: () -> Long,
    private val checkIntervalMs: Long,
    private val firstBeatDelayMs: Long,
    private val lookaheadToleranceMs: Long,
    private val requestAudioFocus: () -> Boolean
) {
    var delegate: PolyrhythmAudioEngineDelegate? = null

    var hasPendingStart: Boolean = false
        private set

    val isRunning: Boolean
        get() = isPlaying

    private var isPlaying = false
    private var pendingBpm = 120f
    private var pendingBeats = 3
    private var pendingAgainst = 2

    private var bpm = 120f
    private var against = 2
    private var beats = 3
    private var grid = PolyrhythmGrid.create(beats = 3, against = 2)
    private var stepDurationNanos = 0L
    private var stepIndex = 0
    private var nextStepTimeNanos = 0L

    fun start(bpm: Float, beats: Int, against: Int) {
        handler.removeCallbacks(runnable)
        if (!isLoaded()) {
            pendingBpm = bpm
            pendingBeats = beats
            pendingAgainst = against
            hasPendingStart = true
            return
        }
        doStart(bpm, beats, against)
    }

    fun stop() {
        isPlaying = false
        hasPendingStart = false
        handler.removeCallbacks(runnable)
        stepIndex = 0
    }

    fun onSoundsLoaded() {
        if (hasPendingStart) {
            hasPendingStart = false
            doStart(pendingBpm, pendingBeats, pendingAgainst)
        }
    }

    private fun doStart(bpm: Float, beats: Int, against: Int) {
        if (!requestAudioFocus()) return

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
        handler.post(runnable)
    }

    private val runnable = object : Runnable {
        override fun run() {
            checkAndPlayStep()
            if (isPlaying) {
                handler.postDelayed(this, checkIntervalMs)
            }
        }
    }

    private fun checkAndPlayStep() {
        if (!isPlaying) {
            handler.removeCallbacks(runnable)
            return
        }

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val lookaheadNanos = lookaheadToleranceMs * 1_000_000L

        if (nowNanos >= nextStepTimeNanos - lookaheadNanos) {
            playCurrentStep(nextStepTimeNanos)
            // Increment from the scheduled time (not nowNanos) so late callbacks self-correct
            nextStepTimeNanos += stepDurationNanos
            stepIndex = (stepIndex + 1) % grid.lcm
        }
    }

    private fun playCurrentStep(scheduledTimeNanos: Long) {
        val step = grid.stepAt(stepIndex)
        val beatFired = step.beatFired
        val rhythmFired = step.rhythmFired
        if (!beatFired && !rhythmFired) return

        if (!isMuted()) {
            when {
                beatFired && rhythmFired -> playBeatAndRhythmSounds()
                beatFired -> playBeatSound()
                rhythmFired -> playRhythmSound()
            }
        }

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
}
