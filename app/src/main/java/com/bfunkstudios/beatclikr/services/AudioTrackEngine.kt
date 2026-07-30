package com.bfunkstudios.beatclikr.services

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.PlaybackInputResult
import com.bfunkstudios.beatclikr.music.SessionID
import com.bfunkstudios.beatclikr.music.SessionOrigin
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayDeque

/** Low-latency metronome output using cached mono PCM files. */
data class AudioTrackMetricsSnapshot(
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
    val underrunCount: Int
)

class AudioTrackEngine(
    private val audioManager: AudioManager? = null,
    private val pcmFileCache: PcmFileCache
) {
    private val renderThread = HandlerThread("AudioTrackRenderThread").also { it.start() }
    private val renderHandler = Handler(renderThread.looper)
    private val pendingClicks = ArrayDeque<ShortArray>()
    private val pendingClicksLock = Any()
    private val activeClicks = mutableListOf<ActiveClick>()

    private var audioTrack: AudioTrack? = null
    private var sampleRate = pcmFileCache.sampleRate
    private var outputFramesPerBuffer = resolveOutputFramesPerBuffer()
    private var bufferSizeInBytes = 0
    private var renderChunkFrames = defaultRenderChunkFrames()
    private var renderBuffer = ShortArray(renderChunkFrames)
    private val soundSelection = PreparedSoundSelection(
        initialBank = SoundBank.ACOUSTIC,
        initialBeatSound = SoundFile.CLICK_HI,
        initialRhythmSound = SoundFile.CLICK_LO,
        provider = PreparedBankProvider { bank, sounds ->
            pcmFileCache.prepare(sounds, bank)
        }
    )

    private var renderRunning = false
    private var frameSession: AudioTrackFrameSession? = null
    private var nextSessionID = 1L

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
    private var queuedClicks = 0L

    @Volatile
    private var queuedBeatClicks = 0L

    @Volatile
    private var queuedRhythmClicks = 0L

    @Volatile
    private var renderedChunks = 0L

    @Volatile
    private var writtenFrames = 0L

    @Volatile
    private var maxActiveClicks = 0

    @Volatile
    var estimatedOutputLatencyNanos: Long = 0L
        private set

    fun metricsSnapshot(): AudioTrackMetricsSnapshot {
        val frame = frameSession?.snapshot()
        if (frame != null && (frame.properties != null || frame.renderedBlocks > 0)) {
            val properties = frame.properties
            val rate = properties?.sampleRate ?: sampleRate
            val burst = properties?.burstFrames ?: outputFramesPerBuffer
            val bufferFrames = properties?.bufferFrames ?: 0
            return AudioTrackMetricsSnapshot(
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
                writtenFrames = frame.nextFrame - frame.firstOutputFrame,
                maxActiveClicks = 0,
                underrunCount = frame.underrunCount
            )
        }
        return AudioTrackMetricsSnapshot(
            sampleRate = sampleRate,
            outputFramesPerBuffer = outputFramesPerBuffer,
            bufferSizeInBytes = bufferSizeInBytes,
            renderChunkFrames = renderChunkFrames,
            estimatedOutputLatencyNanos = estimatedOutputLatencyNanos,
            queuedClicks = queuedClicks,
            queuedBeatClicks = queuedBeatClicks,
            queuedRhythmClicks = queuedRhythmClicks,
            renderedChunks = renderedChunks,
            writtenFrames = writtenFrames,
            maxActiveClicks = maxActiveClicks,
            underrunCount = audioTrack?.underrunCount ?: 0
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

    fun start() {
        renderHandler.post {
            val track = ensureAudioTrack()
            if (renderRunning) return@post

            activeClicks.clear()
            synchronized(pendingClicksLock) { pendingClicks.clear() }
            track.flush()
            track.play()
            renderRunning = true
            renderHandler.post(renderRunnable)
        }
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
        renderHandler.post {
            ensureAudioTrack()
        }
    }

    fun playBeat() {
        soundSelection.active?.beat?.let { enqueueWaveform(it, isBeat = true) }
    }

    fun playRhythm() {
        soundSelection.active?.rhythm?.let { enqueueWaveform(it, isBeat = false) }
    }

    fun playBeatAndRhythm() {
        val selected = soundSelection.active ?: return
        synchronized(pendingClicksLock) {
            pendingClicks.addLast(selected.beat)
            pendingClicks.addLast(selected.rhythm)
            queuedClicks += 2L
            queuedBeatClicks++
            queuedRhythmClicks++
        }
    }

    fun stop() {
        frameSession?.stop()
        renderHandler.post {
            renderRunning = false
            renderHandler.removeCallbacks(renderRunnable)
            activeClicks.clear()
            synchronized(pendingClicksLock) { pendingClicks.clear() }
            val track = audioTrack ?: return@post
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.flush()
        }
    }

    fun release() {
        frameSession?.release()
        frameSession = null
        val latch = CountDownLatch(1)
        renderHandler.post {
            renderRunning = false
            renderHandler.removeCallbacks(renderRunnable)
            activeClicks.clear()
            synchronized(pendingClicksLock) { pendingClicks.clear() }
            audioTrack?.release()
            audioTrack = null
            latch.countDown()
        }
        latch.await(1, TimeUnit.SECONDS)
        renderThread.quitSafely()
    }

    private fun enqueueWaveform(waveform: ShortArray, isBeat: Boolean) {
        synchronized(pendingClicksLock) {
            pendingClicks.addLast(waveform)
            queuedClicks += 1L
            if (isBeat) queuedBeatClicks++ else queuedRhythmClicks++
        }
    }

    private fun startFramePublication(result: FramePublicationResult): Boolean {
        val ready = result as? FramePublicationResult.Ready ?: return false
        val session = frameSession ?: AudioTrackFrameSession(
            audioManager,
            preferredSampleRate = sampleRate,
            preferredBurstFrames = outputFramesPerBuffer
        ).also { frameSession = it }
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

    private fun nextOrigin(): SessionOrigin {
        val origin = SessionOrigin(SessionID(nextSessionID), 0)
        nextSessionID = Math.incrementExact(nextSessionID)
        return origin
    }

    private val renderRunnable = object : Runnable {
        override fun run() {
            if (!renderRunning) return

            drainPendingClicks()
            renderBuffer.fill(0)
            mixActiveClicks()
            audioTrack?.write(renderBuffer, 0, renderBuffer.size, AudioTrack.WRITE_BLOCKING)
            renderedChunks++
            writtenFrames += renderBuffer.size

            if (renderRunning) {
                renderHandler.post(this)
            }
        }
    }

    private fun drainPendingClicks() {
        synchronized(pendingClicksLock) {
            while (pendingClicks.isNotEmpty()) {
                activeClicks += ActiveClick(pendingClicks.removeFirst())
            }
            maxActiveClicks = maxOf(maxActiveClicks, activeClicks.size)
        }
    }

    private fun mixActiveClicks() {
        val iterator = activeClicks.iterator()
        while (iterator.hasNext()) {
            val click = iterator.next()
            var bufferIndex = 0
            while (bufferIndex < renderBuffer.size && click.position < click.waveform.size) {
                val mixed = renderBuffer[bufferIndex].toInt() + click.waveform[click.position].toInt()
                renderBuffer[bufferIndex] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                bufferIndex++
                click.position++
            }
            if (click.position >= click.waveform.size) {
                iterator.remove()
            }
        }
    }

    private fun ensureAudioTrack(): AudioTrack {
        audioTrack?.let { return it }

        val minimumBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val fallbackBufferSize = BYTES_PER_SAMPLE * DEFAULT_BUFFER_FRAMES
        bufferSizeInBytes = if (minimumBufferSize > 0) {
            minimumBufferSize
        } else {
            fallbackBufferSize
        }
        configureRenderBuffer(bytesToFrames(bufferSizeInBytes).toInt())
        // Buffer drain time is a lower-bound latency estimate; device HAL latency is not exposed here.
        estimatedOutputLatencyNanos = estimateLatencyNanos(bytesToFrames(bufferSizeInBytes))
        val attributesBuilder = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            attributesBuilder.setFlags(AudioAttributes.FLAG_LOW_LATENCY)
        }
        val attributes = attributesBuilder.build()
        val format = AudioFormat.Builder()
            .setEncoding(AUDIO_FORMAT)
            .setSampleRate(sampleRate)
            .setChannelMask(CHANNEL_CONFIG)
            .build()
        val builder = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        return builder.build().also { audioTrack = it }
    }

    private class ActiveClick(
        val waveform: ShortArray,
        var position: Int = 0
    )

    private fun resolveOutputFramesPerBuffer(): Int {
        val value = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
        return value ?: DEFAULT_OUTPUT_FRAMES_PER_BUFFER
    }

    private fun defaultRenderChunkFrames(): Int {
        return (outputFramesPerBuffer / 2)
            .coerceIn(MIN_RENDER_CHUNK_FRAMES, MAX_RENDER_CHUNK_FRAMES)
    }

    private fun configureRenderBuffer(bufferFrames: Int) {
        val targetFrames = when {
            outputFramesPerBuffer > 0 -> outputFramesPerBuffer / 2
            else -> bufferFrames / 4
        }.coerceIn(MIN_RENDER_CHUNK_FRAMES, MAX_RENDER_CHUNK_FRAMES)

        if (targetFrames != renderChunkFrames) {
            renderChunkFrames = targetFrames
            renderBuffer = ShortArray(renderChunkFrames)
        }
    }

    private fun estimateLatencyNanos(bufferFrames: Long): Long {
        val outputBurstFrames = outputFramesPerBuffer.toLong().coerceAtLeast(0L)
        return (bufferFrames + outputBurstFrames) * NANOS_PER_SECOND / sampleRate
    }

    private companion object {
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val DEFAULT_BUFFER_FRAMES = 1_024
        const val DEFAULT_OUTPUT_FRAMES_PER_BUFFER = 192
        const val MIN_RENDER_CHUNK_FRAMES = 64
        const val MAX_RENDER_CHUNK_FRAMES = 512
        const val NANOS_PER_SECOND = 1_000_000_000L

        fun bytesToFrames(bytes: Int): Long = (bytes / BYTES_PER_SAMPLE).toLong().coerceAtLeast(1L)
    }
}
