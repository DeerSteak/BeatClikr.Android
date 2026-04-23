package com.bfunkstudios.beatclikr.services

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.bfunkstudios.beatclikr.constants.MetronomeConstants

interface MetronomeAudioEngineDelegate {
    fun metronomeBeatFired(isBeat: Boolean)
}

class MetronomeAudioEngine(private val context: Context) {
    private val soundPool: SoundPool
    private val handlerThread = HandlerThread("MetronomeThread").also { it.start() }
    private val handler = Handler(handlerThread.looper)

    private var beatSoundId: Int = 0
    private var rhythmSoundId: Int = 0
    private var beatLoaded = false
    private var rhythmLoaded = false

    private var isPlaying: Boolean = false
    private var currentBPM: Float = 60f
    private var currentSubdivisions: Int = 1
    private var subdivisionCounter: Int = 0
    private var nextBeatTimeNanos: Long = 0L

    private var delegate: MetronomeAudioEngineDelegate? = null

    @Volatile var isMuted: Boolean = false

    private var pendingBpm: Float = 60f
    private var pendingSubdivisions: Int = 1
    private var pendingDelegate: MetronomeAudioEngineDelegate? = null
    private var hasPendingStart = false

    private val checkInterval = MetronomeConstants.TIMER_CHECK_INTERVAL_MS
    private val firstBeatDelayMs = MetronomeConstants.FIRST_BEAT_DELAY_MS
    private val lookaheadToleranceMs = MetronomeConstants.LOOKAHEAD_TOLERANCE_MS

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            handler.post {
                if (status == 0) {
                    if (sampleId == beatSoundId) beatLoaded = true
                    if (sampleId == rhythmSoundId) rhythmLoaded = true

                    if (beatLoaded && rhythmLoaded && hasPendingStart) {
                        hasPendingStart = false
                        pendingDelegate?.let { doStart(pendingBpm, pendingSubdivisions, it) }
                    }
                }
            }
        }
    }

    fun loadSounds(beatResourceId: Int, rhythmResourceId: Int) {
        handler.post {
            beatLoaded = false
            rhythmLoaded = false
            hasPendingStart = false
            beatSoundId = soundPool.load(context, beatResourceId, 1)
            rhythmSoundId = soundPool.load(context, rhythmResourceId, 1)
        }
    }

    fun startMetronome(bpm: Float, subdivisions: Int, delegate: MetronomeAudioEngineDelegate) {
        handler.post {
            handler.removeCallbacks(timerRunnable)
            if (!beatLoaded || !rhythmLoaded) {
                pendingBpm = bpm
                pendingSubdivisions = subdivisions
                pendingDelegate = delegate
                hasPendingStart = true
                return@post
            }
            doStart(bpm, subdivisions, delegate)
        }
    }

    fun stopMetronome() {
        handler.post {
            isPlaying = false
            hasPendingStart = false
            handler.removeCallbacks(timerRunnable)
            subdivisionCounter = 0
        }
    }

    fun updateTempo(bpm: Float, subdivisions: Int) {
        handler.post {
            currentBPM = bpm
            currentSubdivisions = subdivisions
        }
    }

    fun release() {
        handler.post {
            isPlaying = false
            hasPendingStart = false
            handler.removeCallbacks(timerRunnable)
            soundPool.release()
            delegate = null
        }
        handlerThread.quitSafely()
    }

    private fun doStart(bpm: Float, subdivisions: Int, delegate: MetronomeAudioEngineDelegate) {
        this.delegate = delegate
        this.currentBPM = bpm
        this.currentSubdivisions = subdivisions
        this.subdivisionCounter = 0

        val currentTimeNanos = SystemClock.elapsedRealtimeNanos()
        this.nextBeatTimeNanos = currentTimeNanos + (firstBeatDelayMs * 1_000_000L)

        this.isPlaying = true
        startTimer()
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

        if (!isMuted) {
            if (isBeat) {
                soundPool.play(beatSoundId, 1f, 1f, 1, 0, 1f)
            } else {
                soundPool.play(rhythmSoundId, 1f, 1f, 1, 0, 1f)
            }
        }

        delegate?.metronomeBeatFired(isBeat)
    }
}
