package com.bfunkstudios.beatclikr.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
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

sealed interface AudioEngineStartResult {
    data class Started(val evidence: FrameAudioStartEvidence) : AudioEngineStartResult
    data object AudioFocusUnavailable : AudioEngineStartResult
    data object StreamFailed : AudioEngineStartResult
}

class MetronomeAudioEngine(private val context: Context) : PlaybackEnginePort {
    @Volatile
    override var soundPreparationObserver: ((SoundPreparationPublication) -> Unit)? = null
    @Volatile
    override var transportObserver: PlaybackEngineTransportObserver? = null
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
    private val activeOutputRoute = ActiveOutputRouteTracker()
    @Volatile
    private var activeCoordinatorSessionId: PlaybackSessionId? = null

    override var delegate: MetronomeAudioEngineDelegate? = null

    override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate?
        get() = polyrhythmEngine.delegate
        set(value) { polyrhythmEngine.delegate = value }

    @Volatile
    override var isMuted: Boolean = false
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
                val sessionId = activeCoordinatorSessionId ?: return@OnAudioFocusChangeListener
                publishInterruption(
                    sessionId,
                    PlaybackInterruptionReason.AudioFocusLost
                )
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

    private val audioDeviceTopologyMonitor = AudioDeviceTopologyMonitor(
        register = { audioManager.registerAudioDeviceCallback(it, handler) },
        unregister = audioManager::unregisterAudioDeviceCallback,
        onTopologyChanged = ::checkRouteAfterDeviceTopologyChange
    )

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
                publishSoundPreparation(requestSequence)
                return@post
            }
            this.beatResourceId = beatResourceId
            this.rhythmResourceId = rhythmResourceId
            getOrCreateFrameAudioEngine().setSounds(beatResourceId, rhythmResourceId)
            publishSoundPreparation(requestSequence)
        }
    }

    override fun selectSounds(
        requestSequence: Long,
        beatResourceId: Int,
        rhythmResourceId: Int
    ) = loadSounds(beatResourceId, rhythmResourceId, requestSequence)

    override fun selectSoundBank(requestSequence: Long, bank: SoundBank) {
        selectSoundBank(bank, requestSequence)
    }

    override fun prepareSounds(requestSequence: Long, sounds: Collection<SoundFile>) {
        prepareAudioTrackSounds(sounds, requestSequence)
    }

    override fun prewarmAudioTrack() = prewarm()

    override fun activeSoundConfiguration(): ActiveSoundConfiguration? =
        getActiveSoundConfiguration()

    override fun soundPreparationFailure(): SoundPreparationFailure? =
        getSoundPreparationFailure()

    override fun beginStandardSession(
        sessionId: PlaybackSessionId,
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {
        val callback = delegate
        if (callback == null) {
            transportObserver?.engineStartFailed(sessionId, "Metronome delegate unavailable")
            return
        }
        startMetronome(
            bpm,
            subdivisions,
            accentPattern,
            alternateSixteenth,
            callback,
            sessionId,
            ::publishStartResult
        )
    }

    override fun beginPolyrhythmSession(
        sessionId: PlaybackSessionId,
        bpm: Float,
        beats: Int,
        against: Int
    ) = startPolyrhythm(sessionId, bpm, beats, against, ::publishStartResult)

    fun selectSoundBank(bank: SoundBank, requestSequence: Long?) {
        requestedSoundBank = bank
        handler.post {
            frameAudioEngine?.soundBank = bank
            publishSoundPreparation(requestSequence)
        }
    }

    fun startMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        delegate: MetronomeAudioEngineDelegate,
        sessionId: PlaybackSessionId,
        completion: (PlaybackSessionId, AudioEngineStartResult) -> Unit
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

    fun startPolyrhythm(
        sessionId: PlaybackSessionId,
        bpm: Float,
        beats: Int,
        against: Int,
        completion: (PlaybackSessionId, AudioEngineStartResult) -> Unit
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
                completion(sessionId, AudioEngineStartResult.AudioFocusUnavailable)
                return@post
            }
            activeCoordinatorSessionId = sessionId
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
                abandonAudioFocus()
                completion(sessionId, AudioEngineStartResult.StreamFailed)
                return@post
            }
            val evidence = engine.startEvidence()
            if (evidence == null) {
                engine.stop()
                frameAudioActive = false
                framePolyrhythmActive = false
                activeCoordinatorSessionId = null
                abandonAudioFocus()
                completion(sessionId, AudioEngineStartResult.StreamFailed)
                return@post
            }
            activeOutputRoute.begin(evidence.route)
            polyrhythmEngine.start(bpm, beats, against)
            polyrhythmPlaying = true
            completion(sessionId, AudioEngineStartResult.Started(evidence))
        }
    }

    fun stopSession(sessionId: PlaybackSessionId, mode: PlaybackMode, completion: () -> Unit) {
        handler.post {
            if (activeCoordinatorSessionId != sessionId) {
                completion()
                return@post
            }
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
            activeOutputRoute.clear()
            abandonAudioFocus()
            completion()
        }
    }

    override fun stopSession(sessionId: PlaybackSessionId, mode: PlaybackMode) {
        stopSession(sessionId, mode) { transportObserver?.engineStopped(sessionId) }
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
            publishSoundPreparation(requestSequence)
        }
    }

    override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? {
        return frameAudioEngine?.metricsSnapshot()
    }

    override fun drainRenderedEvents(afterCaptureSequence: Long): FrameAudioRenderedEventBatch? =
        frameAudioEngine?.drainRenderedEvents(afterCaptureSequence)

    fun getSoundPreparationFailure(): SoundPreparationFailure? =
        frameAudioEngine?.lastSoundPreparationFailure

    fun getFramePublicationFailure(): FramePublicationResult.Rejected? =
        frameAudioEngine?.lastFramePublicationFailure

    fun getActiveSoundConfiguration(): ActiveSoundConfiguration? =
        frameAudioEngine?.activeSoundConfiguration

    private fun publishSoundPreparation(requestSequence: Long? = null) {
        val engine = frameAudioEngine
        val active = engine?.activeSoundConfiguration
        val failure = engine?.lastSoundPreparationFailure
        val sessionId = activeCoordinatorSessionId
        if (sessionId != null && active != null && failure == null) {
            val queued = engine.adoptPreparedSounds { accepted ->
                val adoptionFailure = if (accepted) null else SoundPreparationFailure(
                    active.bank,
                    active.beatSound,
                    SoundPreparationFailureCode.RENDERER_REJECTED
                )
                notifySoundPublication(
                    requestSequence,
                    sessionId,
                    accepted,
                    active,
                    adoptionFailure
                )
            }
            if (queued) return
            notifySoundPublication(
                requestSequence,
                sessionId,
                false,
                active,
                SoundPreparationFailure(
                    active.bank,
                    active.beatSound,
                    SoundPreparationFailureCode.RENDERER_REJECTED
                )
            )
            return
        }
        notifySoundPublication(requestSequence, sessionId, false, active, failure)
    }

    private fun notifySoundPublication(
        requestSequence: Long?,
        sessionId: PlaybackSessionId?,
        adopted: Boolean,
        active: ActiveSoundConfiguration?,
        failure: SoundPreparationFailure?
    ) {
        soundPreparationObserver?.invoke(
            SoundPreparationPublication(
                requestSequence,
                sessionId,
                adopted,
                active,
                failure
            )
        )
    }

    override fun updateStandardSession(
        sessionId: PlaybackSessionId,
        configuration: CommittedPlaybackConfiguration.Standard,
        completion: (PlaybackEngineUpdateResult) -> Unit
    ) {
        handler.post {
            val rejection = when {
                activeCoordinatorSessionId != sessionId -> PlaybackCoordinatorFailureCode.STALE_SESSION
                !isPlaying || !frameAudioActive -> PlaybackCoordinatorFailureCode.MODE_MISMATCH
                framePolyrhythmActive -> PlaybackCoordinatorFailureCode.MODE_MISMATCH
                else -> null
            }
            if (rejection != null) {
                completion(PlaybackEngineUpdateResult.Rejected(sessionId, rejection))
                return@post
            }
            try {
                val accepted = requireNotNull(frameAudioEngine).updateStandard(
                    configuration.bpm,
                    configuration.subdivisions,
                    configuration.accentPattern,
                    configuration.alternateSixteenth,
                    isMuted
                )
                if (!accepted) {
                    completion(
                        PlaybackEngineUpdateResult.Rejected(
                            sessionId,
                            PlaybackCoordinatorFailureCode.RENDERER_REJECTED
                        )
                    )
                    return@post
                }
                currentBPM = configuration.bpm
                currentSubdivisions = configuration.subdivisions
                currentAccentPattern = configuration.accentPattern
                currentAlternateSixteenth = configuration.alternateSixteenth
                if (currentAccentPattern != null && subdivisionCounter >= currentAccentPattern!!.size) {
                    subdivisionCounter = 0
                }
                completion(PlaybackEngineUpdateResult.Accepted(sessionId))
            } catch (failure: IllegalArgumentException) {
                completion(updateFailure(sessionId, PlaybackCoordinatorFailureCode.INVALID_INPUT, failure))
            } catch (failure: Throwable) {
                completion(updateFailure(sessionId, PlaybackCoordinatorFailureCode.ENGINE_FAILURE, failure))
            }
        }
    }

    override fun updatePolyrhythmSession(
        sessionId: PlaybackSessionId,
        configuration: CommittedPlaybackConfiguration.Polyrhythm,
        completion: (PlaybackEngineUpdateResult) -> Unit
    ) {
        handler.post {
            val rejection = when {
                activeCoordinatorSessionId != sessionId -> PlaybackCoordinatorFailureCode.STALE_SESSION
                !polyrhythmPlaying || !frameAudioActive || !framePolyrhythmActive ->
                    PlaybackCoordinatorFailureCode.MODE_MISMATCH
                else -> null
            }
            if (rejection != null) {
                completion(PlaybackEngineUpdateResult.Rejected(sessionId, rejection))
                return@post
            }
            try {
                val accepted = requireNotNull(frameAudioEngine).updatePolyrhythm(
                    configuration.bpm,
                    configuration.beats,
                    configuration.against,
                    isMuted
                )
                if (!accepted) {
                    completion(
                        PlaybackEngineUpdateResult.Rejected(
                            sessionId,
                            PlaybackCoordinatorFailureCode.RENDERER_REJECTED
                        )
                    )
                    return@post
                }
                polyrhythmEngine.updateAtCycleBoundary(
                    configuration.bpm,
                    configuration.beats,
                    configuration.against
                )
                completion(PlaybackEngineUpdateResult.Accepted(sessionId))
            } catch (failure: IllegalArgumentException) {
                completion(updateFailure(sessionId, PlaybackCoordinatorFailureCode.INVALID_INPUT, failure))
            } catch (failure: Throwable) {
                completion(updateFailure(sessionId, PlaybackCoordinatorFailureCode.ENGINE_FAILURE, failure))
            }
        }
    }

    private fun updateFailure(
        sessionId: PlaybackSessionId,
        reason: PlaybackCoordinatorFailureCode,
        failure: Throwable
    ) = PlaybackEngineUpdateResult.Rejected(
        sessionId,
        reason,
        failure.message ?: failure::class.java.simpleName
    )

    override fun release() {
        val latch = CountDownLatch(1)
        handler.post {
            isPlaying = false
            handler.removeCallbacks(timerRunnable)
            frameAudioEngine?.release()
            frameAudioEngine = null
            polyrhythmEngine.stop()
            polyrhythmEngine.delegate = null
            delegate = null
            transportObserver = null
            audioDeviceTopologyMonitor.release()
            abandonAudioFocus()
            latch.countDown()
        }
        latch.await(1, TimeUnit.SECONDS)
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
        sessionId: PlaybackSessionId,
        completion: (PlaybackSessionId, AudioEngineStartResult) -> Unit
    ) {
        if (!requestAudioFocus()) {
            completion(sessionId, AudioEngineStartResult.AudioFocusUnavailable)
            return
        }
        activeCoordinatorSessionId = sessionId

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
            this.delegate = null
            abandonAudioFocus()
            completion(sessionId, AudioEngineStartResult.StreamFailed)
            return
        }
        val evidence = engine.startEvidence()
        if (evidence == null) {
            engine.stop()
            frameAudioActive = false
            this.delegate = null
            activeCoordinatorSessionId = null
            abandonAudioFocus()
            completion(sessionId, AudioEngineStartResult.StreamFailed)
            return
        }
        activeOutputRoute.begin(evidence.route)
        this.isPlaying = true
        startTimer()
        completion(sessionId, AudioEngineStartResult.Started(evidence))
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
        return frameAudioEngine ?: FrameAudioEngine(
            audioManager,
            pcmFileCache,
            ::onOutputRouteChanged
        ).also { engine ->
            frameAudioEngine = engine
            engine.soundBank = soundBank
            val beatResource = beatResourceId
            val rhythmResource = rhythmResourceId
            if (beatResource != null && rhythmResource != null) {
                engine.setSounds(beatResource, rhythmResource)
            }
        }
    }

    private fun onOutputRouteChanged(current: AudioOutputRoute) {
        handler.post { applyObservedRoute(current) }
    }

    @VisibleForTesting
    internal fun audioDeviceCallbackForTesting() =
        audioDeviceTopologyMonitor.callbackForTesting()

    @VisibleForTesting
    internal fun prepareRouteWiringForTesting() {
        getOrCreateFrameAudioEngine()
    }

    @VisibleForTesting
    internal fun awaitRouteWiringIdleForTesting(): Boolean {
        val latch = CountDownLatch(1)
        handler.post(latch::countDown)
        return latch.await(1, TimeUnit.SECONDS)
    }

    @VisibleForTesting
    internal fun prepareActiveRouteForTesting(
        sessionId: PlaybackSessionId,
        route: AudioOutputRoute
    ) {
        activeCoordinatorSessionId = sessionId
        activeOutputRoute.begin(route)
    }

    private fun checkRouteAfterDeviceTopologyChange() {
        handler.post {
            applyObservedRoute(frameAudioEngine?.currentRoute() ?: return@post)
        }
    }

    private fun applyObservedRoute(current: AudioOutputRoute) {
        val sessionId = activeCoordinatorSessionId ?: return
        val reason = activeOutputRoute.observe(current) ?: return
        publishInterruption(sessionId, reason)
    }

    private fun publishInterruption(
        sessionId: PlaybackSessionId,
        reason: PlaybackInterruptionReason
    ) {
        transportObserver?.engineInterrupted(sessionId, reason)
    }

    private fun publishStartResult(
        sessionId: PlaybackSessionId,
        result: AudioEngineStartResult
    ) {
        when (result) {
            AudioEngineStartResult.AudioFocusUnavailable ->
                transportObserver?.audioFocusUnavailable(sessionId)
            AudioEngineStartResult.StreamFailed ->
                transportObserver?.engineStartFailed(sessionId, "Audio stream failed to start")
            is AudioEngineStartResult.Started -> {
                val sounds = activeSoundConfiguration()
                if (sounds == null) {
                    transportObserver?.engineStartFailed(
                        sessionId,
                        "Audio stream started without prepared sounds"
                    )
                    return
                }
                transportObserver?.engineStarted(
                    PlaybackEngineStartEvidence(
                        sessionId,
                        sounds,
                        result.evidence.route,
                        result.evidence.backend,
                        result.evidence.firstEventFrame
                    )
                )
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
