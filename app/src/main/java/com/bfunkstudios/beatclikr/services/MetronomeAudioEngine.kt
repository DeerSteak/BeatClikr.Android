package com.bfunkstudios.beatclikr.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
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
    @Volatile
    private var audioTrackEngine: AudioTrackEngine? = null
    private val handlerThread = HandlerThread("MetronomeThread").also { it.start() }
    private val handler = Handler(handlerThread.looper)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val pcmFileCache = PcmFileCache(context, resolveOutputSampleRate())

    private var beatResourceId: Int? = null
    private var rhythmResourceId: Int? = null

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
    var soundBank: SoundBank = SoundBank.ACOUSTIC
        set(value) {
            field = value
            handler.post {
                audioTrackEngine?.soundBank = value
            }
        }

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

    private val polyrhythmEngine = PolyrhythmTimingEngine(
        handler = handler,
        isMuted = { isMuted },
        isLoaded = { true },
        playBeatSound = { audioTrackEngine?.playBeat() },
        playRhythmSound = { audioTrackEngine?.playRhythm() },
        playBeatAndRhythmSounds = { audioTrackEngine?.playBeatAndRhythm() },
        outputLatencyNanos = { if (!isMuted) audioTrackEngine?.estimatedOutputLatencyNanos ?: 0L else 0L },
        checkIntervalMs = checkInterval,
        firstBeatDelayMs = firstBeatDelayMs,
        lookaheadToleranceMs = lookaheadToleranceMs,
        requestAudioFocus = ::requestAudioFocus
    )

    fun loadSounds(beatResourceId: Int, rhythmResourceId: Int) {
        handler.post {
            if (this.beatResourceId == beatResourceId && this.rhythmResourceId == rhythmResourceId) return@post
            this.beatResourceId = beatResourceId
            this.rhythmResourceId = rhythmResourceId
            getOrCreateAudioTrackEngine().setSounds(beatResourceId, rhythmResourceId)
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
            doStart(bpm, subdivisions, accentPattern, alternateSixteenth, delegate)
        }
    }

    fun stopMetronome() {
        handler.post {
            isPlaying = false
            handler.removeCallbacks(timerRunnable)
            audioTrackEngine?.stop()
            subdivisionCounter = 0
        }
    }

    fun startPolyrhythm(bpm: Float, beats: Int, against: Int) {
        handler.post {
            getOrCreateAudioTrackEngine().start()
            polyrhythmEngine.start(bpm, beats, against)
        }
    }

    fun stopPolyrhythm() {
        handler.post {
            polyrhythmEngine.stop()
            audioTrackEngine?.stop()
        }
    }

    fun prewarm() {
        handler.post {
            getOrCreateAudioTrackEngine().prewarm()
        }
    }

    fun prepareAudioTrackSounds(soundFiles: Collection<SoundFile>) {
        handler.post {
            pcmFileCache.prepare(soundFiles, soundBank)
            audioTrackEngine?.prepareSounds(soundFiles)
        }
    }

    fun getAudioTrackMetricsSnapshot(): AudioTrackMetricsSnapshot? {
        return audioTrackEngine?.metricsSnapshot()
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
            handler.removeCallbacks(timerRunnable)
            audioTrackEngine?.release()
            audioTrackEngine = null
            polyrhythmEngine.stop()
            polyrhythmEngine.delegate = null
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
        getOrCreateAudioTrackEngine().start()
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
            if (shouldPlayBeatSound) {
                audioTrackEngine?.playBeat()
            } else {
                audioTrackEngine?.playRhythm()
            }
        }

        val visualBeatTimeNanos = scheduledTimeNanos +
            if (!isMuted) audioTrackEngine?.estimatedOutputLatencyNanos ?: 0L else 0L
        delegate?.metronomeBeatFired(isBeat, beatInterval, visualBeatTimeNanos)
    }

    private fun getOrCreateAudioTrackEngine(): AudioTrackEngine {
        return audioTrackEngine ?: AudioTrackEngine(audioManager, pcmFileCache).also { engine ->
            audioTrackEngine = engine
            engine.soundBank = soundBank
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

    private fun resolveOutputSampleRate(): Int {
        val value = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
        return value ?: DEFAULT_SAMPLE_RATE
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100
    }
}
