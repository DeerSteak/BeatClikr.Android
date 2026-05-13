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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

interface MetronomeAudioEngineDelegate {
    fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float, beatTimeNanos: Long = 0L)
}

interface PolyrhythmAudioEngineDelegate {
    fun polyrhythmBeatFired(
        beatFired: Boolean,
        rhythmFired: Boolean,
        beatIndex: Int,
        rhythmIndex: Int,
        stepTimeNanos: Long = 0L,
        beatDurationNanos: Long = 0L,
        rhythmDurationNanos: Long = 0L
    )
}

class MetronomeAudioEngine(private val context: Context) {
    private val soundPool: SoundPool
    private var audioTrackEngine: AudioTrackEngine? = null
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

    var polyrhythmDelegate: PolyrhythmAudioEngineDelegate?
        get() = polyrhythmEngine.delegate
        set(value) { polyrhythmEngine.delegate = value }

    @Volatile var isMuted: Boolean = false
    @Volatile
    var useAudioTrack: Boolean = false
        set(value) {
            field = value
            handler.post {
                // isPlaying tracks metronome playback only; polyrhythm does not set it.
                if (!isPlaying) return@post
                if (value) {
                    getOrCreateAudioTrackEngine().start()
                } else {
                    audioTrackEngine?.stop()
                }
            }
        }

    private var pendingBpm: Float = 60f
    private var pendingSubdivisions: Int = 1
    private var pendingAccentPattern: List<Boolean>? = null
    private var pendingAlternateSixteenth = false
    private var pendingDelegate: MetronomeAudioEngineDelegate? = null
    private var hasPendingStart = false

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

    private val polyrhythmEngine: PolyrhythmTimingEngine

    init {
        soundPool = SoundPool.Builder()
            .setMaxStreams(MetronomeConstants.MAX_SOUND_STREAMS)
            .setAudioAttributes(audioAttributes)
            .build()

        polyrhythmEngine = PolyrhythmTimingEngine(
            handler = handler,
            soundPool = soundPool,
            isMuted = { isMuted },
            isLoaded = { beatLoaded && rhythmLoaded },
            checkIntervalMs = checkInterval,
            firstBeatDelayMs = firstBeatDelayMs,
            lookaheadToleranceMs = lookaheadToleranceMs,
            requestAudioFocus = ::requestAudioFocus
        )

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
                        polyrhythmEngine.onSoundsLoaded()
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
            polyrhythmEngine.stop()
            this.beatResourceId = beatResourceId
            this.rhythmResourceId = rhythmResourceId
            beatSoundId = soundPool.load(context, beatResourceId, 1)
            rhythmSoundId = soundPool.load(context, rhythmResourceId, 1)
            polyrhythmEngine.beatSoundId = beatSoundId
            polyrhythmEngine.rhythmSoundId = rhythmSoundId
            audioTrackEngine?.setSounds(beatResourceId, rhythmResourceId)
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
            if (!useAudioTrack && (!beatLoaded || !rhythmLoaded)) {
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
            audioTrackEngine?.stop()
            subdivisionCounter = 0
        }
    }

    fun startPolyrhythm(bpm: Float, beats: Int, against: Int) {
        handler.post {
            audioTrackEngine?.stop()
            polyrhythmEngine.start(bpm, beats, against)
        }
    }

    fun stopPolyrhythm() {
        handler.post { polyrhythmEngine.stop() }
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
            hasPendingStart = false
            handler.removeCallbacks(timerRunnable)
            audioTrackEngine?.release()
            audioTrackEngine = null
            polyrhythmEngine.stop()
            polyrhythmEngine.delegate = null
            soundPool.release()
            delegate = null
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
        if (useAudioTrack) {
            getOrCreateAudioTrackEngine().start()
        }
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
            playCurrentBeat(subdivisionDurationNanos, nextBeatTimeNanos)

            // Increment from the scheduled time (not nowNanos) so late callbacks self-correct
            nextBeatTimeNanos += subdivisionDurationNanos

            subdivisionCounter++
            if (subdivisionCounter >= currentStepCount()) {
                subdivisionCounter = 0
            }
        }
    }

    private fun playCurrentBeat(subdivisionDurationNanos: Long, scheduledTimeNanos: Long) {
        val accentPattern = currentAccentPattern
        val isBeat = accentPattern?.getOrNull(subdivisionCounter) ?: (subdivisionCounter == 0)
        val ticksToNextBeat = accentPattern?.let { ticksToNextAccent(it, subdivisionCounter) }
            ?: currentSubdivisions
        val beatInterval = ticksToNextBeat * (subdivisionDurationNanos / 1_000_000_000f)
        val shouldPlayBeatSound = isBeat ||
            (accentPattern == null && currentAlternateSixteenth && currentSubdivisions == 4 && subdivisionCounter == 2)

        if (!isMuted) {
            if (useAudioTrack) {
                if (shouldPlayBeatSound) {
                    audioTrackEngine?.playBeat()
                } else {
                    audioTrackEngine?.playRhythm()
                }
            } else {
                if (shouldPlayBeatSound) {
                    soundPool.play(beatSoundId, 1f, 1f, 1, 0, 1f)
                } else {
                    soundPool.play(rhythmSoundId, 1f, 1f, 1, 0, 1f)
                }
            }
        }

        val visualBeatTimeNanos = if (useAudioTrack && !isMuted) {
            scheduledTimeNanos + (audioTrackEngine?.estimatedOutputLatencyNanos ?: 0L)
        } else {
            scheduledTimeNanos
        }
        delegate?.metronomeBeatFired(isBeat, beatInterval, visualBeatTimeNanos)
    }

    private fun getOrCreateAudioTrackEngine(): AudioTrackEngine {
        return audioTrackEngine ?: AudioTrackEngine().also { engine ->
            audioTrackEngine = engine
            val beatResource = beatResourceId
            val rhythmResource = rhythmResourceId
            if (beatResource != null && rhythmResource != null) {
                engine.setSounds(beatResource, rhythmResource)
            }
        }
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
}
