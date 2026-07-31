package com.bfunkstudios.beatclikr.services

import android.media.AudioManager
import androidx.annotation.VisibleForTesting
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.PlaybackInputResult
import com.bfunkstudios.beatclikr.music.SessionID
import com.bfunkstudios.beatclikr.music.SessionOrigin

/** Low-latency metronome output using cached mono PCM files. */
data class FrameAudioMetricsSnapshot(
    val backend: AudioBackendType,
    val route: AudioOutputRoute,
    val sampleRate: Int,
    val channelCount: Int,
    val outputFramesPerBuffer: Int,
    val bufferFrames: Int,
    val performanceMode: AudioBackendPerformanceMode,
    val bufferSizeInBytes: Int,
    val renderChunkFrames: Int,
    val estimatedOutputLatencyNanos: Long,
    val queuedClicks: Long,
    val queuedBeatClicks: Long,
    val queuedRhythmClicks: Long,
    val renderedChunks: Long,
    val intendedFrames: Long,
    val renderedFrames: Long,
    val writtenFrames: Long,
    val estimatedPresentedFrames: Long?,
    val mixDurationP50UpperBoundNanos: Long,
    val mixDurationP95UpperBoundNanos: Long,
    val mixDurationP99UpperBoundNanos: Long,
    val maximumMixDurationNanos: Long,
    val writeDurationP50UpperBoundNanos: Long,
    val writeDurationP95UpperBoundNanos: Long,
    val writeDurationP99UpperBoundNanos: Long,
    val maximumWriteDurationNanos: Long,
    val routeChangeCount: Long,
    val deadlineMisses: Long,
    val droppedEvents: Long,
    val maxActiveClicks: Int,
    val underrunCount: Int,
    val underrunSkippedFrames: Long,
    val frameCorrelation: AudioFrameCorrelation?,
    val latestBackendFailure: AudioBackendFailure?
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
    private var nextSessionID = 1L
    private val renderedEvents = RenderedEventRing(RENDERED_EVENT_CAPACITY)

    @Volatile
    var lastFramePublicationFailure: FramePublicationResult.Rejected? = null
        private set

    var soundBank: SoundBank
        get() = soundSelection.requestedBank
        set(value) {
            soundSelection.selectBank(value)
        }

    val lastSoundPreparationFailure: SoundPreparationFailure?
        get() = soundSelection.failure

    val activeSoundBank: SoundBank?
        get() = soundSelection.active?.bank

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
            backend = AudioBackendType.UNKNOWN,
            route = AudioOutputRoute.UNKNOWN,
            sampleRate = sampleRate,
            channelCount = 1,
            outputFramesPerBuffer = outputFramesPerBuffer,
            bufferFrames = 0,
            performanceMode = AudioBackendPerformanceMode.UNKNOWN,
            bufferSizeInBytes = 0,
            renderChunkFrames = outputFramesPerBuffer,
            estimatedOutputLatencyNanos = estimatedOutputLatencyNanos,
            queuedClicks = 0,
            queuedBeatClicks = 0,
            queuedRhythmClicks = 0,
            renderedChunks = 0,
            intendedFrames = 0,
            renderedFrames = 0,
            writtenFrames = 0,
            estimatedPresentedFrames = null,
            mixDurationP50UpperBoundNanos = 0,
            mixDurationP95UpperBoundNanos = 0,
            mixDurationP99UpperBoundNanos = 0,
            maximumMixDurationNanos = 0,
            writeDurationP50UpperBoundNanos = 0,
            writeDurationP95UpperBoundNanos = 0,
            writeDurationP99UpperBoundNanos = 0,
            maximumWriteDurationNanos = 0,
            routeChangeCount = 0,
            deadlineMisses = 0,
            droppedEvents = 0,
            maxActiveClicks = 0,
            underrunCount = 0,
            underrunSkippedFrames = 0,
            frameCorrelation = null,
            latestBackendFailure = null
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

    fun updateStandard(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        muted: Boolean
    ): Boolean {
        val configuration = when (
            val result = FramePlaybackPublicationBoundary.standardConfiguration(
                bpm = bpm,
                subdivisions = subdivisions,
                accentPattern = accentPattern,
                alternateSixteenth = alternateSixteenth,
                muted = muted
            )
        ) {
            is PlaybackInputResult.Accepted -> result.value
            is PlaybackInputResult.Rejected -> return false
        }
        return frameSession?.updateStandard(configuration) == true
    }

    fun updatePolyrhythm(
        bpm: Float,
        beats: Int,
        against: Int,
        muted: Boolean
    ): Boolean {
        val configuration = when (
            val result = FramePlaybackPublicationBoundary.polyrhythmConfiguration(
                bpm = bpm,
                beats = beats,
                against = against,
                muted = muted
            )
        ) {
            is PlaybackInputResult.Accepted -> result.value
            is PlaybackInputResult.Rejected -> return false
        }
        return frameSession?.updatePolyrhythm(configuration) == true
    }

    fun startStandard(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        muted: Boolean,
        startDelayMillis: Long,
        sessionId: PlaybackSessionId? = null
    ): Boolean = startFramePublication(
        FramePlaybackPublicationBoundary.standard(
            bpm = bpm,
            subdivisions = subdivisions,
            accentPattern = accentPattern,
            alternateSixteenth = alternateSixteenth,
            muted = muted,
            origin = nextOrigin(sessionId),
            sounds = soundSelection.active,
            startDelayMillis = startDelayMillis,
            eventCapture = renderedEvents
        )
    )

    fun startPolyrhythm(
        bpm: Float,
        beats: Int,
        against: Int,
        muted: Boolean,
        startDelayMillis: Long,
        sessionId: PlaybackSessionId? = null
    ): Boolean = startFramePublication(
        FramePlaybackPublicationBoundary.polyrhythm(
            bpm = bpm,
            beats = beats,
            against = against,
            muted = muted,
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

    @VisibleForTesting
    internal fun reportRouteChangeForTesting(
        previous: AudioOutputRoute,
        current: AudioOutputRoute
    ) {
        frameSession().reportRouteChangeForTesting(previous, current)
    }

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
            is FramePublicationResult.Rejected -> {
                lastFramePublicationFailure = result
                return false
            }
        }
        lastFramePublicationFailure = null
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

    private fun nextOrigin(sessionId: PlaybackSessionId?): SessionOrigin {
        val value = sessionId?.value ?: nextSessionID
        val origin = SessionOrigin(SessionID(value), 0)
        nextSessionID = maxOf(nextSessionID, Math.incrementExact(value))
        return origin
    }

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
