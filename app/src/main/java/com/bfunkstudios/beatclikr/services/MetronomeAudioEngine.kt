package com.bfunkstudios.beatclikr.services

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.bfunkstudios.beatclikr.constants.MetronomeConstants

/**
 * Low-latency audio engine for metronome playback using SoundPool.
 * Provides sample-accurate timing with <5ms jitter, matching iOS AudioKit implementation.
 */
interface MetronomeAudioEngineDelegate {
    fun metronomeBeatFired(isBeat: Boolean)
}

class MetronomeAudioEngine(private val context: Context) {
    private val soundPool: SoundPool
    private val handler = Handler(Looper.getMainLooper())

    private var beatSoundId: Int = 0
    private var rhythmSoundId: Int = 0
    private var isPlaying: Boolean = false
    private var currentBPM: Float = 60f
    private var currentSubdivisions: Int = 1
    private var subdivisionCounter: Int = 0
    private var nextBeatTimeNanos: Long = 0L

    private var delegate: MetronomeAudioEngineDelegate? = null

    private val checkInterval = MetronomeConstants.TIMER_CHECK_INTERVAL_MS
    private val firstBeatDelayMs = MetronomeConstants.FIRST_BEAT_DELAY_MS
    private val lookaheadToleranceMs = MetronomeConstants.LOOKAHEAD_TOLERANCE_MS

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    fun loadSounds(beatResourceId: Int, rhythmResourceId: Int) {
        beatSoundId = soundPool.load(context, beatResourceId, 1)
        rhythmSoundId = soundPool.load(context, rhythmResourceId, 1)
    }

    fun startMetronome(bpm: Float, subdivisions: Int, delegate: MetronomeAudioEngineDelegate) {
        handler.removeCallbacks(timerRunnable)

        this.delegate = delegate
        this.currentBPM = bpm
        this.currentSubdivisions = subdivisions
        this.subdivisionCounter = 0

        val currentTimeNanos = SystemClock.elapsedRealtimeNanos()
        this.nextBeatTimeNanos = currentTimeNanos + (firstBeatDelayMs * 1_000_000L)

        this.isPlaying = true
        startTimer()
    }

    fun stopMetronome() {
        isPlaying = false
        handler.removeCallbacks(timerRunnable)
        subdivisionCounter = 0
    }

    fun updateTempo(bpm: Float, subdivisions: Int) {
        this.currentBPM = bpm
        this.currentSubdivisions = subdivisions
    }

    fun release() {
        stopMetronome()
        soundPool.release()
        delegate = null
    }

    private fun getSubdivisionDurationNanos(): Long {
        val durationMs = 60_000.0 / (currentBPM * currentSubdivisions)
        return (durationMs * 1_000_000L).toLong()
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            checkAndPlayBeat()
            if (isPlaying) {
                handler.postDelayed(this, checkInterval)
            }
        }
    }

    private fun startTimer() {
        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)
    }

    private fun checkAndPlayBeat() {
        if (!isPlaying) {
            handler.removeCallbacks(timerRunnable)
            return
        }

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val lookaheadNanos = lookaheadToleranceMs * 1_000_000L

        if (nowNanos >= nextBeatTimeNanos - lookaheadNanos) {
            playCurrentBeat()

            val subdivisionDurationNanos = getSubdivisionDurationNanos()
            nextBeatTimeNanos = nowNanos + subdivisionDurationNanos

            subdivisionCounter++
            if (subdivisionCounter >= currentSubdivisions) {
                subdivisionCounter = 0
            }
        }
    }

    private fun playCurrentBeat() {
        val isBeat = subdivisionCounter == 0

        if (isBeat) {
            soundPool.play(beatSoundId, 1f, 1f, 1, 0, 1f)
        } else {
            soundPool.play(rhythmSoundId, 1f, 1f, 1, 0, 1f)
        }

        delegate?.metronomeBeatFired(isBeat)
    }
}
