package com.bfunkstudios.beatclikr.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.VisibleForTesting
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    private var activeMode = PlaybackMode.NONE
    private var audioFocusHeld = false
    private val activeOutputRoute = ActiveOutputRouteTracker()
    @Volatile
    private var activeCoordinatorSessionId: PlaybackSessionId? = null

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
            applySoundBank(value, null)
        }

    private val firstBeatDelayMs = MetronomeConstants.FIRST_BEAT_DELAY_MS

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

    override fun selectSounds(
        requestSequence: Long,
        beatResourceId: Int,
        rhythmResourceId: Int
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

    override fun selectSoundBank(requestSequence: Long, bank: SoundBank) {
        applySoundBank(bank, requestSequence)
    }

    override fun prepareSounds(requestSequence: Long, sounds: Collection<SoundFile>) {
        handler.post {
            getOrCreateFrameAudioEngine().prepareSounds(sounds)
            publishSoundPreparation(requestSequence)
        }
    }

    override fun prewarmAudioTrack() {
        handler.post { getOrCreateFrameAudioEngine().prewarm() }
    }

    override fun activeSoundConfiguration(): ActiveSoundConfiguration? =
        frameAudioEngine?.activeSoundConfiguration

    override fun soundPreparationFailure(): SoundPreparationFailure? =
        frameAudioEngine?.lastSoundPreparationFailure

    override fun beginStandardSession(
        sessionId: PlaybackSessionId,
        configuration: ValidatedStandardConfiguration
    ) {
        handler.post {
            startSession(sessionId, PlaybackMode.STANDARD, ::publishStartResult) {
                startStandard(configuration, firstBeatDelayMs, sessionId)
            }
        }
    }

    override fun beginPolyrhythmSession(
        sessionId: PlaybackSessionId,
        configuration: ValidatedPolyrhythmConfiguration
    ) = startPolyrhythm(sessionId, configuration, ::publishStartResult)

    private fun applySoundBank(bank: SoundBank, requestSequence: Long?) {
        requestedSoundBank = bank
        handler.post {
            frameAudioEngine?.soundBank = bank
            publishSoundPreparation(requestSequence)
        }
    }

    private fun startPolyrhythm(
        sessionId: PlaybackSessionId,
        configuration: ValidatedPolyrhythmConfiguration,
        completion: (PlaybackSessionId, AudioEngineStartResult) -> Unit
    ) {
        handler.post {
            val engine = getOrCreateFrameAudioEngine()
            if (activeMode == PlaybackMode.POLYRHYTHM) {
                engine.updatePolyrhythm(configuration)
                return@post
            }
            startSession(sessionId, PlaybackMode.POLYRHYTHM, completion, engine) {
                startPolyrhythm(configuration, firstBeatDelayMs, sessionId)
            }
        }
    }

    private fun startSession(
        sessionId: PlaybackSessionId,
        mode: PlaybackMode,
        completion: (PlaybackSessionId, AudioEngineStartResult) -> Unit,
        engine: FrameAudioEngine = getOrCreateFrameAudioEngine(),
        start: FrameAudioEngine.() -> Boolean
    ) {
        if (!requestAudioFocus()) {
            completion(sessionId, AudioEngineStartResult.AudioFocusUnavailable)
            return
        }
        activeCoordinatorSessionId = sessionId
        if (activeMode != PlaybackMode.NONE) engine.stop()
        if (!engine.start()) {
            abandonAudioFocus()
            completion(sessionId, AudioEngineStartResult.StreamFailed)
            return
        }
        val evidence = engine.startEvidence()
        if (evidence == null) {
            engine.stop()
            activeCoordinatorSessionId = null
            abandonAudioFocus()
            completion(sessionId, AudioEngineStartResult.StreamFailed)
            return
        }
        activeOutputRoute.begin(evidence.route)
        activeMode = mode
        completion(sessionId, AudioEngineStartResult.Started(evidence))
    }

    fun stopSession(sessionId: PlaybackSessionId, _mode: PlaybackMode, completion: () -> Unit) {
        handler.post {
            if (activeCoordinatorSessionId != sessionId) {
                completion()
                return@post
            }
            activeMode = PlaybackMode.NONE
            frameAudioEngine?.stop()
            activeCoordinatorSessionId = null
            activeOutputRoute.clear()
            abandonAudioFocus()
            completion()
        }
    }

    override fun stopSession(sessionId: PlaybackSessionId, mode: PlaybackMode) {
        stopSession(sessionId, mode) { transportObserver?.engineStopped(sessionId) }
    }

    override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? {
        return frameAudioEngine?.metricsSnapshot()
    }

    override fun drainRenderedEvents(afterCaptureSequence: Long): FrameAudioRenderedEventBatch? =
        frameAudioEngine?.drainRenderedEvents(afterCaptureSequence)

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
        configuration: ValidatedStandardConfiguration,
        completion: (PlaybackEngineUpdateResult) -> Unit
    ) = updateSession(sessionId, PlaybackMode.STANDARD, completion) {
        updateStandard(configuration)
    }

    override fun updatePolyrhythmSession(
        sessionId: PlaybackSessionId,
        configuration: ValidatedPolyrhythmConfiguration,
        completion: (PlaybackEngineUpdateResult) -> Unit
    ) = updateSession(sessionId, PlaybackMode.POLYRHYTHM, completion) {
        updatePolyrhythm(configuration)
    }

    private fun updateSession(
        sessionId: PlaybackSessionId,
        expectedMode: PlaybackMode,
        completion: (PlaybackEngineUpdateResult) -> Unit,
        update: FrameAudioEngine.() -> Boolean
    ) {
        handler.post {
            val rejection = when {
                activeCoordinatorSessionId != sessionId -> PlaybackCoordinatorFailureCode.STALE_SESSION
                activeMode != expectedMode -> PlaybackCoordinatorFailureCode.MODE_MISMATCH
                else -> null
            }
            if (rejection != null) {
                completion(PlaybackEngineUpdateResult.Rejected(sessionId, rejection))
                return@post
            }
            try {
                val accepted = requireNotNull(frameAudioEngine).update()
                if (!accepted) {
                    completion(
                        PlaybackEngineUpdateResult.Rejected(
                            sessionId,
                            PlaybackCoordinatorFailureCode.RENDERER_REJECTED
                        )
                    )
                    return@post
                }
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
            activeMode = PlaybackMode.NONE
            frameAudioEngine?.release()
            frameAudioEngine = null
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
