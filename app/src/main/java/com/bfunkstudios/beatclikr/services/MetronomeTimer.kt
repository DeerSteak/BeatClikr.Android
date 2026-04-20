package com.bfunkstudios.beatclikr.services

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.bfunkstudios.beatclikr.constants.MetronomeConstants

/**
 * High-precision metronome timer using SystemClock.elapsedRealtimeNanos()
 * for drift-free timing. Matches iOS implementation's accuracy.
 */
interface MetronomeTimerDelegate {
    fun metronomeTimerFired()
}

class MetronomeTimer(
    private var beatsPerMinute: Float = 60f,
    private var subdivisions: Int = 2
) {
    var delegate: MetronomeTimerDelegate? = null

    private val handler = Handler(Looper.getMainLooper())
    private var paused: Boolean = true
    private var previousSubdivisionTimeNanos: Long = 0L

    private val subdivisionCheckInterval: Long
        get() = (getSubdivisionDurationMs() / 50).toLong().coerceAtLeast(1L)

    private val elapsedTimeMs: Double
        get() {
            val currentTimeNanos = SystemClock.elapsedRealtimeNanos()
            return (currentTimeNanos - previousSubdivisionTimeNanos) / 1_000_000.0
        }

    private val timerRunnable = object : Runnable {
        override fun run() {
            checkTimeToPlay()
            if (!paused) {
                handler.postDelayed(this, subdivisionCheckInterval)
            }
        }
    }

    fun updateTempo(bpm: Float, subdivisions: Int) {
        this.beatsPerMinute = bpm
        this.subdivisions = subdivisions
        if (!paused) {
            startTimer()
        }
    }

    fun start() {
        if (paused) {
            paused = false
            previousSubdivisionTimeNanos = SystemClock.elapsedRealtimeNanos()
            startTimer()
        }
    }

    fun stop() {
        if (!paused) {
            paused = true
            handler.removeCallbacks(timerRunnable)
        }
    }

    fun release() {
        stop()
        delegate = null
    }

    private fun getSubdivisionDurationMs(): Double {
        return 60_000.0 / (beatsPerMinute * subdivisions)
    }

    private fun getTimeToNextSubdivision(): Double {
        return if (paused) {
            getSubdivisionDurationMs()
        } else {
            kotlin.math.abs(elapsedTimeMs - getSubdivisionDurationMs())
        }
    }

    private fun startTimer() {
        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)
    }

    private fun checkTimeToPlay() {
        // If past or extremely close to correct duration, play
        if (elapsedTimeMs > getSubdivisionDurationMs() || getTimeToNextSubdivision() < 5.0) {
            timerElapsed()
        }
    }

    private fun timerElapsed() {
        previousSubdivisionTimeNanos = SystemClock.elapsedRealtimeNanos()
        delegate?.metronomeTimerFired()
    }

    companion object {
        val instance = MetronomeTimer()
    }
}
