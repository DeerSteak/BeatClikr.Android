package com.bfunkstudios.beatclikr.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.PolyrhythmGrid
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

interface MetronomeAudioEngineDelegate {
    fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float)
}

interface PolyrhythmAudioEngineDelegate {
    fun polyrhythmBeatFired(
        beatFired: Boolean,
        rhythmFired: Boolean,
        beatIndex: Int,
        rhythmIndex: Int
    )
}

class MetronomeAudioEngine(private val context: Context) {
    private val soundPool: SoundPool
    private val handlerThread = HandlerThread("MetronomeThread").also { it.start() }
    private val handler = Handler(handlerThread.looper)
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var beatSoundId: Int = 0
    private var rhythmSoundId: Int = 0
    private var beatResourceId: Int? = null
    private var rhythmResourceId: Int? = null
    private var beatLoaded = false
    private var rhythmLoaded = false

    private var isPlaying: Boolean = false
    private var currentBPM: Float = 60f
    private var currentSubdivisions: Int = 1
    private var currentAccentPattern: List<Boolean>? = null
    private var currentAlternateSixteenth = false
    private var subdivisionCounter: Int = 0
    private var nextBeatTimeNanos: Long = 0L

    private var delegate: MetronomeAudioEngineDelegate? = null
    var polyrhythmDelegate: PolyrhythmAudioEngineDelegate? = null

    @Volatile var isMuted: Boolean = false

    private var pendingBpm: Float = 60f
    private var pendingSubdivisions: Int = 1
    private var pendingAccentPattern: List<Boolean>? = null
    private var pendingAlternateSixteenth = false
    private var pendingDelegate: MetronomeAudioEngineDelegate? = null
    private var hasPendingStart = false

    private var isPolyrhythmPlaying = false
    private var pendingPolyrhythmBpm = 120f
    private var pendingPolyrhythmBeats = 3
    private var pendingPolyrhythmAgainst = 2
    private var hasPendingPolyrhythmStart = false
    private var polyrhythmBpm = 120f
    private var polyrhythmAgainst = 2
    private var polyrhythmGrid = PolyrhythmGrid.create(beats = 3, against = 2)
    private var polyrhythmStepDurationNanos = 0L
    private var polyrhythmStepIndex = 0
    private var nextPolyrhythmStepTimeNanos = 0L

    private val checkInterval = MetronomeConstants.TIMER_CHECK_INTERVAL_MS
    private val firstBeatDelayMs = MetronomeConstants.FIRST_BEAT_DELAY_MS
    private val lookaheadToleranceMs = MetronomeConstants.LOOKAHEAD_TOLERANCE_MS

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                stopMetronome()
                stopPolyrhythm()
            }
        }
    }

    private val audioFocusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
        } else null

    init {
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            handler.post {
                if (status == 0) {
                    if (sampleId == beatSoundId) beatLoaded = true
                    if (sampleId == rhythmSoundId) rhythmLoaded = true

                    if (beatLoaded && rhythmLoaded) {
                        if (hasPendingStart) {
                            hasPendingStart = false
                            pendingDelegate?.let {
                                doStart(pendingBpm, pendingSubdivisions, pendingAccentPattern, pendingAlternateSixteenth, it)
                            }
                        }
                        if (hasPendingPolyrhythmStart) {
                            hasPendingPolyrhythmStart = false
                            doStartPolyrhythm(pendingPolyrhythmBpm, pendingPolyrhythmBeats, pendingPolyrhythmAgainst)
                        }
                    }
                }
            }
        }
    }

    fun loadSounds(beatResourceId: Int, rhythmResourceId: Int) {
        handler.post {
            val sameResources = this.beatResourceId == beatResourceId && this.rhythmResourceId == rhythmResourceId
            if (sameResources && (beatLoaded && rhythmLoaded || beatSoundId != 0 && rhythmSoundId != 0)) {
                return@post
            }

            beatLoaded = false
            rhythmLoaded = false
            hasPendingStart = false
            hasPendingPolyrhythmStart = false
            this.beatResourceId = beatResourceId
            this.rhythmResourceId = rhythmResourceId
            beatSoundId = soundPool.load(context, beatResourceId, 1)
            rhythmSoundId = soundPool.load(context, rhythmResourceId, 1)
        }
    }

    fun startMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        delegate: MetronomeAudioEngineDelegate
    ) {
        handler.post {
            handler.removeCallbacks(timerRunnable)
            if (!beatLoaded || !rhythmLoaded) {
                pendingBpm = bpm
                pendingSubdivisions = subdivisions
                pendingAccentPattern = accentPattern
                pendingAlternateSixteenth = alternateSixteenth
                pendingDelegate = delegate
                hasPendingStart = true
                return@post
            }
            doStart(bpm, subdivisions, accentPattern, alternateSixteenth, delegate)
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

    fun startPolyrhythm(bpm: Float, beats: Int, against: Int) {
        handler.post {
            handler.removeCallbacks(polyrhythmRunnable)
            if (!beatLoaded || !rhythmLoaded) {
                pendingPolyrhythmBpm = bpm
                pendingPolyrhythmBeats = beats
                pendingPolyrhythmAgainst = against
                hasPendingPolyrhythmStart = true
                return@post
            }
            doStartPolyrhythm(bpm, beats, against)
        }
    }

    fun stopPolyrhythm() {
        handler.post {
            isPolyrhythmPlaying = false
            hasPendingPolyrhythmStart = false
            handler.removeCallbacks(polyrhythmRunnable)
            polyrhythmStepIndex = 0
        }
    }

    fun updateTempo(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {
        handler.post {
            currentBPM = bpm
            currentSubdivisions = subdivisions
            currentAccentPattern = accentPattern
            currentAlternateSixteenth = alternateSixteenth
            if (currentAccentPattern != null && subdivisionCounter >= currentAccentPattern!!.size) {
                subdivisionCounter = 0
            }
        }
    }

    fun release() {
        val latch = CountDownLatch(1)
        handler.post {
            isPlaying = false
            isPolyrhythmPlaying = false
            hasPendingStart = false
            hasPendingPolyrhythmStart = false
            handler.removeCallbacks(timerRunnable)
            handler.removeCallbacks(polyrhythmRunnable)
            soundPool.release()
            delegate = null
            polyrhythmDelegate = null
            latch.countDown()
        }
        latch.await(1, TimeUnit.SECONDS)
        abandonAudioFocus()
        handlerThread.quitSafely()
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun doStart(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        delegate: MetronomeAudioEngineDelegate
    ) {
        if (!requestAudioFocus()) return

        this.delegate = delegate
        this.currentBPM = bpm
        this.currentSubdivisions = subdivisions
        this.currentAccentPattern = accentPattern
        this.currentAlternateSixteenth = alternateSixteenth
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
            val subdivisionDurationNanos = getSubdivisionDurationNanos()
            playCurrentBeat(subdivisionDurationNanos)

            // Increment from the scheduled time (not nowNanos) so late callbacks self-correct
            nextBeatTimeNanos += subdivisionDurationNanos

            subdivisionCounter++
            if (subdivisionCounter >= currentStepCount()) {
                subdivisionCounter = 0
            }
        }
    }

    private fun playCurrentBeat(subdivisionDurationNanos: Long) {
        val accentPattern = currentAccentPattern
        val isBeat = accentPattern?.getOrNull(subdivisionCounter) ?: (subdivisionCounter == 0)
        val ticksToNextBeat = accentPattern?.let { ticksToNextAccent(it, subdivisionCounter) }
            ?: currentSubdivisions
        val beatInterval = ticksToNextBeat * (subdivisionDurationNanos / 1_000_000_000f)
        val shouldPlayBeatSound = isBeat ||
            (accentPattern == null && currentAlternateSixteenth && currentSubdivisions == 4 && subdivisionCounter == 2)

        if (!isMuted) {
            if (shouldPlayBeatSound) {
                soundPool.play(beatSoundId, 1f, 1f, 1, 0, 1f)
            } else {
                soundPool.play(rhythmSoundId, 1f, 1f, 1, 0, 1f)
            }
        }

        delegate?.metronomeBeatFired(isBeat, beatInterval)
    }

    private fun currentStepCount(): Int = currentAccentPattern?.size ?: currentSubdivisions

    private fun ticksToNextAccent(accentPattern: List<Boolean>, currentIndex: Int): Int {
        if (accentPattern.isEmpty()) return currentSubdivisions
        for (offset in 1..accentPattern.size) {
            val nextIndex = (currentIndex + offset) % accentPattern.size
            if (accentPattern[nextIndex]) return offset
        }
        return accentPattern.size
    }

    private val polyrhythmRunnable = object : Runnable {
        override fun run() {
            checkAndPlayPolyrhythmStep()
            if (isPolyrhythmPlaying) {
                handler.postDelayed(this, checkInterval)
            }
        }
    }

    private fun doStartPolyrhythm(bpm: Float, beats: Int, against: Int) {
        if (!requestAudioFocus()) return

        polyrhythmBpm = bpm
        polyrhythmAgainst = against.coerceIn(1, 15)
        polyrhythmGrid = PolyrhythmGrid.create(beats = beats, against = against)
        // Compute directly in nanoseconds to minimize floating-point precision loss
        val nanosPerBeat = 60_000_000_000.0 / polyrhythmBpm
        polyrhythmStepDurationNanos = (polyrhythmAgainst * nanosPerBeat / polyrhythmGrid.lcm).toLong()
        polyrhythmStepIndex = 0
        nextPolyrhythmStepTimeNanos = SystemClock.elapsedRealtimeNanos() + (firstBeatDelayMs * 1_000_000L)
        isPolyrhythmPlaying = true
        handler.removeCallbacks(polyrhythmRunnable)
        handler.post(polyrhythmRunnable)
    }

    private fun checkAndPlayPolyrhythmStep() {
        if (!isPolyrhythmPlaying) {
            handler.removeCallbacks(polyrhythmRunnable)
            return
        }

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val lookaheadNanos = lookaheadToleranceMs * 1_000_000L

        if (nowNanos >= nextPolyrhythmStepTimeNanos - lookaheadNanos) {
            playCurrentPolyrhythmStep()
            // Increment from the scheduled time (not nowNanos) so late callbacks self-correct
            nextPolyrhythmStepTimeNanos += polyrhythmStepDurationNanos
            polyrhythmStepIndex = (polyrhythmStepIndex + 1) % polyrhythmGrid.lcm
        }
    }

    private fun playCurrentPolyrhythmStep() {
        val step = polyrhythmGrid.stepAt(polyrhythmStepIndex)
        val beatFired = step.beatFired
        val rhythmFired = step.rhythmFired
        if (!beatFired && !rhythmFired) return

        if (!isMuted) {
            when {
                beatFired && rhythmFired -> {
                    // Beat sound first so the more prominent hit leads; reduces perceived flam
                    soundPool.play(beatSoundId, 1f, 1f, 1, 0, 1f)
                    soundPool.play(rhythmSoundId, 1f, 1f, 1, 0, 1f)
                }
                beatFired -> soundPool.play(beatSoundId, 1f, 1f, 1, 0, 1f)
                rhythmFired -> soundPool.play(rhythmSoundId, 1f, 1f, 1, 0, 1f)
            }
        }

        polyrhythmDelegate?.polyrhythmBeatFired(
            beatFired = beatFired,
            rhythmFired = rhythmFired,
            beatIndex = step.beatIndex,
            rhythmIndex = step.rhythmIndex
        )
    }
}
