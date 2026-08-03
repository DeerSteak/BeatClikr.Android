package com.bfunkstudios.beatclikr.services

import android.media.AudioManager
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.SessionID
import com.bfunkstudios.beatclikr.music.SessionOrigin

/** Low-latency metronome output using cached mono PCM files. */
data class FrameAudioMetricsSnapshot(
    val backend: AudioBackendType = AudioBackendType.UNKNOWN,
    val route: AudioOutputRoute = AudioOutputRoute.UNKNOWN,
    val sampleRate: Int = 0,
    val channelCount: Int = 1,
    val outputFramesPerBuffer: Int = 0,
    val bufferFrames: Int = 0,
    val performanceMode: AudioBackendPerformanceMode = AudioBackendPerformanceMode.UNKNOWN,
    val bufferSizeInBytes: Int = 0,
    val renderChunkFrames: Int = 0,
    val estimatedOutputLatencyNanos: Long = 0,
    val queuedClicks: Long = 0,
    val queuedBeatClicks: Long = 0,
    val queuedRhythmClicks: Long = 0,
    val renderedChunks: Long = 0,
    val intendedFrames: Long = 0,
    val renderedFrames: Long = 0,
    val writtenFrames: Long = 0,
    val estimatedPresentedFrames: Long? = null,
    val mixDurationP50UpperBoundNanos: Long = 0,
    val mixDurationP95UpperBoundNanos: Long = 0,
    val mixDurationP99UpperBoundNanos: Long = 0,
    val maximumMixDurationNanos: Long = 0,
    val writeDurationP50UpperBoundNanos: Long = 0,
    val writeDurationP95UpperBoundNanos: Long = 0,
    val writeDurationP99UpperBoundNanos: Long = 0,
    val maximumWriteDurationNanos: Long = 0,
    val routeChangeCount: Long = 0,
    val deadlineMisses: Long = 0,
    val droppedEvents: Long = 0,
    val maxActiveClicks: Int = 0,
    val underrunCount: Int = 0,
    val underrunSkippedFrames: Long = 0,
    val frameCorrelation: AudioFrameCorrelation? = null,
    val latestBackendFailure: AudioBackendFailure? = null
)

data class FrameAudioStartEvidence(
    val route: AudioOutputRoute,
    val backend: AudioBackendType,
    val firstEventFrame: Long
)

data class FrameAudioRenderedEventBatch(
    val events: RenderedEventBatch,
    val sampleRate: Int,
    val correlation: AudioFrameCorrelation?
)

class FrameAudioEngine(
    private val audioManager: AudioManager? = null,
    private val pcmFileCache: PcmFileCache,
    private val routeChangeObserver: (AudioOutputRoute) -> Unit = {}
) {
    private val sampleRate = pcmFileCache.sampleRate
    private val outputFramesPerBuffer = resolveOutputFramesPerBuffer()
    private val soundSelection = PreparedSoundSelection(
        initialBank = SoundBank.ACOUSTIC,
        initialBeatSound = SoundFile.CLICK_HI,
        initialRhythmSound = SoundFile.CLICK_LO,
        provider = PreparedBankProvider { bank, sounds ->
            pcmFileCache.prepare(sounds, bank)
        }
    )

    private var frameSession: AudioTrackFrameSession? = null
    private val renderedEvents = RenderedEventRing(RENDERED_EVENT_CAPACITY)

    var soundBank: SoundBank
        get() = soundSelection.requestedBank
        set(value) {
            soundSelection.selectBank(value)
        }

    val lastSoundPreparationFailure: SoundPreparationFailure?
        get() = soundSelection.failure

    val activeSoundConfiguration: ActiveSoundConfiguration?
        get() = soundSelection.active?.configuration

    @Volatile
    var estimatedOutputLatencyNanos: Long = 0L
        private set

    fun metricsSnapshot(): FrameAudioMetricsSnapshot {
        val frame = frameSession?.snapshot()
        if (frame != null && (frame.properties != null || frame.renderedBlocks > 0)) {
            val properties = frame.properties
            val rate = properties?.sampleRate ?: sampleRate
            val burst = properties?.burstFrames ?: outputFramesPerBuffer
            val bufferFrames = properties?.bufferFrames ?: 0
            return FrameAudioMetricsSnapshot(
                backend = properties?.backend ?: AudioBackendType.UNKNOWN,
                route = frame.route,
                sampleRate = rate,
                channelCount = properties?.channelCount ?: 1,
                outputFramesPerBuffer = burst,
                bufferFrames = bufferFrames,
                performanceMode = properties?.performanceMode
                    ?: AudioBackendPerformanceMode.UNKNOWN,
                bufferSizeInBytes = bufferFrames * 2 * (properties?.channelCount ?: 1),
                renderChunkFrames = burst,
                estimatedOutputLatencyNanos =
                    (bufferFrames + burst).toLong() * NANOS_PER_SECOND / rate,
                queuedClicks = frame.renderedBeatEvents + frame.renderedRhythmEvents,
                queuedBeatClicks = frame.renderedBeatEvents,
                queuedRhythmClicks = frame.renderedRhythmEvents,
                renderedChunks = frame.renderedBlocks,
                intendedFrames = (frame.nextFrame - frame.firstOutputFrame).coerceAtLeast(0L),
                renderedFrames = frame.renderedFrames,
                writtenFrames = frame.writtenFrames,
                estimatedPresentedFrames = frame.estimatedPresentedFrames,
                mixDurationP50UpperBoundNanos = frame.mixDuration.p50UpperBoundNanos,
                mixDurationP95UpperBoundNanos = frame.mixDuration.p95UpperBoundNanos,
                mixDurationP99UpperBoundNanos = frame.mixDuration.p99UpperBoundNanos,
                maximumMixDurationNanos = frame.mixDuration.maximumNanos,
                writeDurationP50UpperBoundNanos = frame.writeDuration.p50UpperBoundNanos,
                writeDurationP95UpperBoundNanos = frame.writeDuration.p95UpperBoundNanos,
                writeDurationP99UpperBoundNanos = frame.writeDuration.p99UpperBoundNanos,
                maximumWriteDurationNanos = frame.writeDuration.maximumNanos,
                routeChangeCount = frame.routeChangeCount,
                deadlineMisses = frame.deadlineMisses,
                droppedEvents = frame.droppedEvents,
                maxActiveClicks = 0,
                underrunCount = frame.underrunCount,
                underrunSkippedFrames = frame.underrunSkippedFrames,
                frameCorrelation = frame.frameCorrelation,
                latestBackendFailure = frame.failures.lastOrNull()
            )
        }
        return FrameAudioMetricsSnapshot(
            sampleRate = sampleRate,
            outputFramesPerBuffer = outputFramesPerBuffer,
            renderChunkFrames = outputFramesPerBuffer,
            estimatedOutputLatencyNanos = estimatedOutputLatencyNanos
        )
    }

    fun setSounds(beatResourceId: Int, rhythmResourceId: Int) {
        val beatSound = SoundFile.fromResourceId(beatResourceId) ?: SoundFile.CLICK_HI
        val rhythmSound = SoundFile.fromResourceId(rhythmResourceId) ?: SoundFile.CLICK_LO
        soundSelection.selectSounds(beatSound, rhythmSound)
    }

    fun prepareSounds(soundFiles: Collection<SoundFile>) {
        soundSelection.includeAndPrepare(soundFiles)
    }

    fun adoptPreparedSounds(completion: (Boolean) -> Unit): Boolean {
        val sounds = soundSelection.active ?: return false
        return frameSession?.updateSounds(sounds, completion) == true
    }

    fun setFrameMuted(muted: Boolean) {
        frameSession?.setMuted(muted)
    }

    fun updateStandard(configuration: ValidatedStandardConfiguration): Boolean =
        frameSession?.updateStandard(configuration.render) == true

    fun updatePolyrhythm(configuration: ValidatedPolyrhythmConfiguration): Boolean =
        frameSession?.updatePolyrhythm(configuration.render) == true

    fun startStandard(
        configuration: ValidatedStandardConfiguration,
        startDelayMillis: Long,
        sessionId: PlaybackSessionId
    ): Boolean = startFramePublication(
        FramePlaybackPublicationBoundary.standard(
            configuration = configuration.render,
            origin = nextOrigin(sessionId),
            sounds = soundSelection.active,
            startDelayMillis = startDelayMillis,
            eventCapture = renderedEvents
        )
    )

    fun startPolyrhythm(
        configuration: ValidatedPolyrhythmConfiguration,
        startDelayMillis: Long,
        sessionId: PlaybackSessionId
    ): Boolean = startFramePublication(
        FramePlaybackPublicationBoundary.polyrhythm(
            configuration = configuration.render,
            origin = nextOrigin(sessionId),
            sounds = soundSelection.active,
            startDelayMillis = startDelayMillis,
            eventCapture = renderedEvents
        )
    )

    fun prewarm() {
        frameSession()
    }

    fun stop() {
        frameSession?.stop()
    }

    fun startEvidence(): FrameAudioStartEvidence? {
        val snapshot = frameSession?.snapshot() ?: return null
        val properties = snapshot.properties ?: return null
        return FrameAudioStartEvidence(
            route = snapshot.route,
            backend = properties.backend,
            firstEventFrame = snapshot.firstEventFrame
        )
    }

    fun currentRoute(): AudioOutputRoute =
        frameSession?.currentRoute() ?: AudioOutputRoute.UNKNOWN

    fun drainRenderedEvents(afterCaptureSequence: Long): FrameAudioRenderedEventBatch {
        val metrics = metricsSnapshot()
        return FrameAudioRenderedEventBatch(
            renderedEvents.drain(afterCaptureSequence),
            metrics.sampleRate,
            metrics.frameCorrelation
        )
    }

    fun release() {
        frameSession?.release()
        frameSession = null
    }

    private fun startFramePublication(result: FramePublicationResult): Boolean {
        val ready = when (result) {
            is FramePublicationResult.Ready -> result
            is FramePublicationResult.Rejected -> return false
        }
        val session = frameSession()
        val started = session.start(ready.factory)
        if (started) {
            estimatedOutputLatencyNanos = session.snapshot().let { snapshot ->
                val properties = snapshot.properties
                if (properties == null) {
                    0
                } else {
                    (properties.bufferFrames + properties.burstFrames).toLong() *
                        NANOS_PER_SECOND / properties.sampleRate
                }
            }
        }
        return started
    }

    private fun frameSession(): AudioTrackFrameSession =
        frameSession ?: AudioTrackFrameSession(
            audioManager,
            preferredSampleRate = sampleRate,
            preferredBurstFrames = outputFramesPerBuffer,
            routeChangeObserver = routeChangeObserver
        ).also { frameSession = it }

    private fun nextOrigin(sessionId: PlaybackSessionId): SessionOrigin =
        SessionOrigin(SessionID(sessionId.value), 0)

    private fun resolveOutputFramesPerBuffer(): Int {
        val value = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
        return value ?: DEFAULT_OUTPUT_FRAMES_PER_BUFFER
    }

    private companion object {
        const val DEFAULT_OUTPUT_FRAMES_PER_BUFFER = 192
        const val RENDERED_EVENT_CAPACITY = 512
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
