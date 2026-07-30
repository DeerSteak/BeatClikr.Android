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
    fun metronomeStartFailed()
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

    fun polyrhythmStartFailed()
}

sealed interface AudioEngineStartResult {
    data class Started(val evidence: FrameAudioStartEvidence) : AudioEngineStartResult
    data object AudioFocusUnavailable : AudioEngineStartResult
    data object StreamFailed : AudioEngineStartResult
}

class MetronomeAudioEngine(private val context: Context) {
    @Volatile
    var soundPreparationObserver: ((SoundPreparationPublication) -> Unit)? = null
    @Volatile
    var playbackInterruptionObserver:
        ((PlaybackSessionId, PlaybackInterruptionReason) -> Unit)? = null
    @Volatile
    private var frameAudioEngine: FrameAudioEngine? = null
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
    private var frameAudioActive = false
    private var framePolyrhythmActive = false
    private var polyrhythmPlaying = false
    private var audioFocusHeld = false
    @Volatile
    private var activeCoordinatorSessionId: PlaybackSessionId? = null

    private var delegate: MetronomeAudioEngineDelegate? = null

    var polyrhythmDelegate: PolyrhythmAudioEngineDelegate?
        get() = polyrhythmEngine.delegate
        set(value) { polyrhythmEngine.delegate = value }

    @Volatile
    var isMuted: Boolean = false
        set(value) {
            field = value
            frameAudioEngine?.setFrameMuted(value)
        }

    @Volatile
    private var requestedSoundBank: SoundBank = SoundBank.ACOUSTIC

    var soundBank: SoundBank
        get() = requestedSoundBank
        set(value) {
            selectSoundBank(value, null)
        }

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
                val sessionId = activeCoordinatorSessionId
                if (sessionId == null) {
                    stopMetronome()
                    stopPolyrhythm()
                } else {
                    playbackInterruptionObserver?.invoke(
                        sessionId,
                        PlaybackInterruptionReason.AudioFocusLost
                    )
                }
            }
        }
    }

    private val audioFocusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
        } else null

    private val polyrhythmEngine = PolyrhythmTimingEngine(
        handler = handler,
        outputLatencyNanos = { if (!isMuted) frameAudioEngine?.estimatedOutputLatencyNanos ?: 0L else 0L },
        firstBeatDelayMs = firstBeatDelayMs,
        lookaheadToleranceMs = lookaheadToleranceMs
    )

    fun loadSounds(
        beatResourceId: Int,
        rhythmResourceId: Int,
        requestSequence: Long? = null
    ) {
        handler.post {
            if (this.beatResourceId == beatResourceId &&
                this.rhythmResourceId == rhythmResourceId) {
                notifySoundPreparation(requestSequence)
                return@post
            }
            this.beatResourceId = beatResourceId
            this.rhythmResourceId = rhythmResourceId
            getOrCreateFrameAudioEngine().setSounds(beatResourceId, rhythmResourceId)
            notifySoundPreparation(requestSequence)
        }
    }

    fun selectSoundBank(bank: SoundBank, requestSequence: Long?) {
        requestedSoundBank = bank
        handler.post {
            frameAudioEngine?.soundBank = bank
            notifySoundPreparation(requestSequence)
        }
    }

    fun startMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        delegate: MetronomeAudioEngineDelegate,
        sessionId: PlaybackSessionId? = null,
        completion: ((PlaybackSessionId, AudioEngineStartResult) -> Unit)? = null
    ) {
        handler.post {
            handler.removeCallbacks(timerRunnable)
            doStart(
                bpm,
                subdivisions,
                accentPattern,
                alternateSixteenth,
                delegate,
                sessionId,
                completion
            )
        }
    }

    fun stopMetronome() {
        handler.post {
            isPlaying = false
            handler.removeCallbacks(timerRunnable)
            frameAudioEngine?.stop()
            frameAudioActive = false
            framePolyrhythmActive = false
            subdivisionCounter = 0
            abandonAudioFocus()
        }
    }

    fun startPolyrhythm(bpm: Float, beats: Int, against: Int) {
        startPolyrhythm(null, bpm, beats, against, null)
    }

    fun startPolyrhythm(
        sessionId: PlaybackSessionId?,
        bpm: Float,
        beats: Int,
        against: Int,
        completion: ((PlaybackSessionId, AudioEngineStartResult) -> Unit)?
    ) {
        handler.post {
            val engine = getOrCreateFrameAudioEngine()
            if (polyrhythmPlaying) {
                if (frameAudioActive && framePolyrhythmActive) {
                    engine.updatePolyrhythm(bpm, beats, against, isMuted)
                }
                polyrhythmEngine.updateAtCycleBoundary(bpm, beats, against)
                return@post
            }
            if (!requestAudioFocus()) {
                sessionId?.let {
                    completion?.invoke(it, AudioEngineStartResult.AudioFocusUnavailable)
                }
                return@post
            }
            if (frameAudioActive) engine.stop()
            frameAudioActive = engine.startPolyrhythm(
                bpm,
                beats,
                against,
                isMuted,
                firstBeatDelayMs,
                sessionId
            )
            framePolyrhythmActive = frameAudioActive
            if (!frameAudioActive) {
                polyrhythmEngine.delegate?.polyrhythmStartFailed()
                abandonAudioFocus()
                sessionId?.let {
                    completion?.invoke(it, AudioEngineStartResult.StreamFailed)
                }
                return@post
            }
            activeCoordinatorSessionId = sessionId
            polyrhythmEngine.start(bpm, beats, against)
            polyrhythmPlaying = true
            sessionId?.let {
                completion?.invoke(
                    it,
                    engine.startEvidence()
                        ?.let(AudioEngineStartResult::Started)
                        ?: AudioEngineStartResult.StreamFailed
                )
            }
        }
    }

    fun stopPolyrhythm() {
        handler.post {
            polyrhythmEngine.stop()
            frameAudioEngine?.stop()
            frameAudioActive = false
            framePolyrhythmActive = false
            polyrhythmPlaying = false
            abandonAudioFocus()
        }
    }

    fun stopSession(mode: PlaybackMode, completion: () -> Unit) {
        handler.post {
            when (mode) {
                PlaybackMode.STANDARD -> {
                    isPlaying = false
                    handler.removeCallbacks(timerRunnable)
                }
                PlaybackMode.POLYRHYTHM -> {
                    polyrhythmEngine.stop()
                    polyrhythmPlaying = false
                }
                PlaybackMode.NONE -> Unit
            }
            frameAudioEngine?.stop()
            frameAudioActive = false
            framePolyrhythmActive = false
            subdivisionCounter = 0
            activeCoordinatorSessionId = null
            abandonAudioFocus()
            completion()
        }
    }

    fun prewarm() {
        handler.post {
            getOrCreateFrameAudioEngine().prewarm()
        }
    }

    fun prepareAudioTrackSounds(
        soundFiles: Collection<SoundFile>,
        requestSequence: Long? = null
    ) {
        handler.post {
            getOrCreateFrameAudioEngine().prepareSounds(soundFiles)
            notifySoundPreparation(requestSequence)
        }
    }

    fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? {
        return frameAudioEngine?.metricsSnapshot()
    }

    fun drainRenderedEvents(afterCaptureSequence: Long): FrameAudioRenderedEventBatch? =
        frameAudioEngine?.drainRenderedEvents(afterCaptureSequence)

    fun getSoundPreparationFailure(): SoundPreparationFailure? =
        frameAudioEngine?.lastSoundPreparationFailure

    fun getFramePublicationFailure(): FramePublicationResult.Rejected? =
        frameAudioEngine?.lastFramePublicationFailure

    fun getActiveSoundBank(): SoundBank? = frameAudioEngine?.activeSoundBank

    fun getActiveSoundConfiguration(): ActiveSoundConfiguration? =
        frameAudioEngine?.activeSoundConfiguration

    private fun notifySoundPreparation(requestSequence: Long? = null) {
        soundPreparationObserver?.invoke(
            SoundPreparationPublication(
                requestSequence,
                getActiveSoundConfiguration(),
                getSoundPreparationFailure()
            )
        )
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
            if (isPlaying && frameAudioActive) {
                if (framePolyrhythmActive) return@post
                val engine = getOrCreateFrameAudioEngine()
                engine.updateStandard(
                    bpm,
                    subdivisions,
                    accentPattern,
                    alternateSixteenth,
                    isMuted
                )
            }
        }
    }

    fun release() {
        val latch = CountDownLatch(1)
        handler.post {
            isPlaying = false
            handler.removeCallbacks(timerRunnable)
            frameAudioEngine?.release()
            frameAudioEngine = null
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
        if (audioFocusHeld) return true
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
        audioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return audioFocusHeld
    }

    private fun abandonAudioFocus() {
        if (!audioFocusHeld) return
        audioFocusHeld = false
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
        delegate: MetronomeAudioEngineDelegate,
        sessionId: PlaybackSessionId?,
        completion: ((PlaybackSessionId, AudioEngineStartResult) -> Unit)?
    ) {
        if (!requestAudioFocus()) {
            sessionId?.let {
                completion?.invoke(it, AudioEngineStartResult.AudioFocusUnavailable)
            }
            return
        }

        polyrhythmPlaying = false
        this.delegate = delegate
        this.currentBPM = bpm
        this.currentSubdivisions = subdivisions
        this.currentAccentPattern = accentPattern
        this.currentAlternateSixteenth = alternateSixteenth
        this.subdivisionCounter = 0

        val currentTimeNanos = SystemClock.elapsedRealtimeNanos()
        this.nextBeatTimeNanos = currentTimeNanos + (firstBeatDelayMs * 1_000_000L)

        val engine = getOrCreateFrameAudioEngine()
        if (frameAudioActive) engine.stop()
        frameAudioActive = engine.startStandard(
            bpm,
            subdivisions,
            accentPattern,
            alternateSixteenth,
            isMuted,
            firstBeatDelayMs,
            sessionId
        )
        framePolyrhythmActive = false
        if (!frameAudioActive) {
            this.delegate?.metronomeStartFailed()
            this.delegate = null
            abandonAudioFocus()
            sessionId?.let {
                completion?.invoke(it, AudioEngineStartResult.StreamFailed)
            }
            return
        }
        this.isPlaying = true
        activeCoordinatorSessionId = sessionId
        startTimer()
        sessionId?.let {
            completion?.invoke(
                it,
                engine.startEvidence()
                    ?.let(AudioEngineStartResult::Started)
                    ?: AudioEngineStartResult.StreamFailed
            )
        }
    }

    private fun getSubdivisionDurationNanos(): Long {
        val durationMs = 60_000.0 / (currentBPM * currentSubdivisions)
        return (durationMs * 1_000_000L).toLong()
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            playScheduledBeat()
            if (isPlaying) scheduleNextBeat()
        }
    }

    private fun startTimer() {
        handler.removeCallbacks(timerRunnable)
        scheduleNextBeat()
    }

    private fun playScheduledBeat() {
        if (!isPlaying) {
            handler.removeCallbacks(timerRunnable)
            return
        }

        val subdivisionDurationNanos = getSubdivisionDurationNanos()
        dropExpiredVisualBeats(subdivisionDurationNanos)
        playCurrentBeat(subdivisionDurationNanos, nextBeatTimeNanos)

        nextBeatTimeNanos += subdivisionDurationNanos

        subdivisionCounter++
        if (subdivisionCounter >= currentStepCount()) {
            subdivisionCounter = 0
        }
    }

    private fun dropExpiredVisualBeats(subdivisionDurationNanos: Long) {
        val skippedBeats = expiredEventCount(
            SystemClock.elapsedRealtimeNanos(),
            nextBeatTimeNanos,
            lookaheadToleranceMs * NANOS_PER_MILLISECOND,
            subdivisionDurationNanos
        )
        if (skippedBeats == 0L) return
        nextBeatTimeNanos = Math.addExact(
            nextBeatTimeNanos,
            Math.multiplyExact(skippedBeats, subdivisionDurationNanos)
        )
        val stepCount = currentStepCount()
        subdivisionCounter = (
            subdivisionCounter + skippedBeats % stepCount
        ).toInt() % stepCount
    }

    private fun scheduleNextBeat() {
        val triggerNanos = nextBeatTimeNanos - lookaheadToleranceMs * NANOS_PER_MILLISECOND
        handler.postDelayed(timerRunnable, delayUntil(triggerNanos))
    }

    private fun playCurrentBeat(subdivisionDurationNanos: Long, scheduledTimeNanos: Long) {
        val accentPattern = currentAccentPattern
        val isBeat = accentPattern?.getOrNull(subdivisionCounter) ?: (subdivisionCounter == 0)
        val ticksToNextBeat = accentPattern?.let { ticksToNextAccent(it, subdivisionCounter) }
            ?: currentSubdivisions
        val beatInterval = ticksToNextBeat * (subdivisionDurationNanos / 1_000_000_000f)
        val visualBeatTimeNanos = scheduledTimeNanos +
            if (!isMuted) frameAudioEngine?.estimatedOutputLatencyNanos ?: 0L else 0L
        delegate?.metronomeBeatFired(isBeat, beatInterval, visualBeatTimeNanos)
    }

    private fun getOrCreateFrameAudioEngine(): FrameAudioEngine {
        return frameAudioEngine ?: FrameAudioEngine(audioManager, pcmFileCache).also { engine ->
            frameAudioEngine = engine
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
        const val NANOS_PER_MILLISECOND = 1_000_000L

        fun delayUntil(triggerNanos: Long): Long {
            val remaining = triggerNanos - SystemClock.elapsedRealtimeNanos()
            return if (remaining <= 0) 0 else (remaining + NANOS_PER_MILLISECOND - 1) /
                NANOS_PER_MILLISECOND
        }
    }
}
