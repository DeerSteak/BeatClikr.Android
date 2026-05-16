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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayDeque

/**
 * Low-latency metronome output using cached mono PCM files.
 */
data class AudioTrackMetricsSnapshot(
    val sampleRate: Int,
    val outputFramesPerBuffer: Int,
    val bufferSizeInBytes: Int,
    val renderChunkFrames: Int,
    val estimatedOutputLatencyNanos: Long,
    val queuedClicks: Long,
    val renderedChunks: Long,
    val writtenFrames: Long,
    val maxActiveClicks: Int
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
    private val waveforms = mutableMapOf<SoundFile, ShortArray>()
    private val waveformLock = Any()
    private var beatSound: SoundFile = SoundFile.CLICK_HI
    private var rhythmSound: SoundFile = SoundFile.CLICK_LO
    private var renderRunning = false

    @Volatile
    var soundBank: SoundBank = SoundBank.ACOUSTIC
        set(value) {
            field = value
            synchronized(waveformLock) { waveforms.clear() }
        }

    @Volatile
    private var queuedClicks = 0L

    @Volatile
    private var renderedChunks = 0L

    @Volatile
    private var writtenFrames = 0L

    @Volatile
    private var maxActiveClicks = 0

    @Volatile
    var estimatedOutputLatencyNanos: Long = 0L
        private set

    fun metricsSnapshot(): AudioTrackMetricsSnapshot = AudioTrackMetricsSnapshot(
        sampleRate = sampleRate,
        outputFramesPerBuffer = outputFramesPerBuffer,
        bufferSizeInBytes = bufferSizeInBytes,
        renderChunkFrames = renderChunkFrames,
        estimatedOutputLatencyNanos = estimatedOutputLatencyNanos,
        queuedClicks = queuedClicks,
        renderedChunks = renderedChunks,
        writtenFrames = writtenFrames,
        maxActiveClicks = maxActiveClicks
    )

    fun setSounds(beatResourceId: Int, rhythmResourceId: Int) {
        beatSound = SoundFile.fromResourceId(beatResourceId) ?: SoundFile.CLICK_HI
        rhythmSound = SoundFile.fromResourceId(rhythmResourceId) ?: SoundFile.CLICK_LO
        ensureWaveform(beatSound)
        ensureWaveform(rhythmSound)
    }

    fun prepareSounds(soundFiles: Collection<SoundFile>) {
        renderHandler.post {
            pcmFileCache.prepare(soundFiles, soundBank)
            synchronized(waveformLock) {
                soundFiles.forEach { waveforms.remove(it) }
            }
        }
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

    fun prewarm() {
        renderHandler.post {
            ensureAudioTrack()
        }
    }

    fun playBeat() {
        enqueueWaveform(ensureWaveform(beatSound))
    }

    fun playRhythm() {
        enqueueWaveform(ensureWaveform(rhythmSound))
    }

    fun playBeatAndRhythm() {
        val beatWaveform = ensureWaveform(beatSound)
        val rhythmWaveform = ensureWaveform(rhythmSound)
        synchronized(pendingClicksLock) {
            pendingClicks.addLast(beatWaveform)
            pendingClicks.addLast(rhythmWaveform)
            queuedClicks += 2L
        }
    }

    fun stop() {
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
        // Release is called during MetronomeAudioEngine shutdown, after audio callbacks are stopped.
        synchronized(waveformLock) { waveforms.clear() }
    }

    private fun enqueueWaveform(waveform: ShortArray) {
        synchronized(pendingClicksLock) {
            pendingClicks.addLast(waveform)
            queuedClicks += 1L
        }
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

    private fun ensureWaveform(soundFile: SoundFile): ShortArray {
        synchronized(waveformLock) {
            waveforms[soundFile]?.let { return it }
        }

        val bank = soundBank
        val waveform = pcmFileCache.load(soundFile, bank) ?: ShortArray(0)

        synchronized(waveformLock) {
            waveforms[soundFile]?.let { return it }
            if (soundBank != bank) return ensureWaveform(soundFile)
            waveforms[soundFile] = waveform
            return waveform
        }
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
