package com.bfunkstudios.beatclikr.services

import android.media.AudioManager
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.PlaybackInputResult
import com.bfunkstudios.beatclikr.music.SessionID
import com.bfunkstudios.beatclikr.music.SessionOrigin

/** Low-latency metronome output using cached mono PCM files. */
data class FrameAudioMetricsSnapshot(
    val sampleRate: Int,
    val outputFramesPerBuffer: Int,
    val bufferSizeInBytes: Int,
    val renderChunkFrames: Int,
    val estimatedOutputLatencyNanos: Long,
    val queuedClicks: Long,
    val queuedBeatClicks: Long,
    val queuedRhythmClicks: Long,
    val renderedChunks: Long,
    val writtenFrames: Long,
    val maxActiveClicks: Int,
    val underrunCount: Int,
    val underrunSkippedFrames: Long,
    val frameCorrelation: AudioFrameCorrelation?
)

class FrameAudioEngine(
    private val audioManager: AudioManager? = null,
    private val pcmFileCache: PcmFileCache
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
                sampleRate = rate,
                outputFramesPerBuffer = burst,
                bufferSizeInBytes = bufferFrames * 2 * (properties?.channelCount ?: 1),
                renderChunkFrames = burst,
                estimatedOutputLatencyNanos =
                    (bufferFrames + burst).toLong() * NANOS_PER_SECOND / rate,
                queuedClicks = frame.renderedBeatEvents + frame.renderedRhythmEvents,
                queuedBeatClicks = frame.renderedBeatEvents,
                queuedRhythmClicks = frame.renderedRhythmEvents,
                renderedChunks = frame.renderedBlocks,
                writtenFrames = frame.writtenFrames,
                maxActiveClicks = 0,
                underrunCount = frame.underrunCount,
                underrunSkippedFrames = frame.underrunSkippedFrames,
                frameCorrelation = frame.frameCorrelation
            )
        }
        return FrameAudioMetricsSnapshot(
            sampleRate = sampleRate,
            outputFramesPerBuffer = outputFramesPerBuffer,
            bufferSizeInBytes = 0,
            renderChunkFrames = outputFramesPerBuffer,
            estimatedOutputLatencyNanos = estimatedOutputLatencyNanos,
            queuedClicks = 0,
            queuedBeatClicks = 0,
            queuedRhythmClicks = 0,
            renderedChunks = 0,
            writtenFrames = 0,
            maxActiveClicks = 0,
            underrunCount = 0,
            underrunSkippedFrames = 0,
            frameCorrelation = null
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
        startDelayMillis: Long
    ): Boolean = startFramePublication(
        FramePlaybackPublicationBoundary.standard(
            bpm = bpm,
            subdivisions = subdivisions,
            accentPattern = accentPattern,
            alternateSixteenth = alternateSixteenth,
            muted = muted,
            origin = nextOrigin(),
            sounds = soundSelection.active,
            startDelayMillis = startDelayMillis
        )
    )

    fun startPolyrhythm(
        bpm: Float,
        beats: Int,
        against: Int,
        muted: Boolean,
        startDelayMillis: Long
    ): Boolean = startFramePublication(
        FramePlaybackPublicationBoundary.polyrhythm(
            bpm = bpm,
            beats = beats,
            against = against,
            muted = muted,
            origin = nextOrigin(),
            sounds = soundSelection.active,
            startDelayMillis = startDelayMillis
        )
    )

    fun prewarm() {
        frameSession()
    }

    fun stop() {
        frameSession?.stop()
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
            preferredBurstFrames = outputFramesPerBuffer
        ).also { frameSession = it }

    private fun nextOrigin(): SessionOrigin {
        val origin = SessionOrigin(SessionID(nextSessionID), 0)
        nextSessionID = Math.incrementExact(nextSessionID)
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
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
