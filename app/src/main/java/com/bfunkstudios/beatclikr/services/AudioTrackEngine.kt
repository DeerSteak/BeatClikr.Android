package com.bfunkstudios.beatclikr.services

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.bfunkstudios.beatclikr.data.SoundFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayDeque
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Low-latency metronome output using generated PCM clicks.
 *
 * Phase 2 keeps timing in MetronomeAudioEngine and uses AudioTrack only as the
 * output primitive. Raw resource decoding can be added later if matching the
 * SoundPool samples exactly becomes more important than startup simplicity.
 */
class AudioTrackEngine {
    private val renderThread = HandlerThread("AudioTrackRenderThread").also { it.start() }
    private val renderHandler = Handler(renderThread.looper)
    private val pendingClicks = ArrayDeque<ShortArray>()
    private val pendingClicksLock = Any()
    private val activeClicks = mutableListOf<ActiveClick>()
    private val renderBuffer = ShortArray(RENDER_CHUNK_FRAMES)

    private var audioTrack: AudioTrack? = null
    private val waveforms = mutableMapOf<SoundFile, ShortArray>()
    private var beatSound: SoundFile = SoundFile.CLICK_HI
    private var rhythmSound: SoundFile = SoundFile.CLICK_LO
    private var renderRunning = false
    @Volatile
    var estimatedOutputLatencyNanos: Long = 0L
        private set

    fun setSounds(beatResourceId: Int, rhythmResourceId: Int) {
        beatSound = SoundFile.fromResourceId(beatResourceId) ?: SoundFile.CLICK_HI
        rhythmSound = SoundFile.fromResourceId(rhythmResourceId) ?: SoundFile.CLICK_LO
        ensureWaveform(beatSound)
        ensureWaveform(rhythmSound)
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
        waveforms.clear()
    }

    private fun enqueueWaveform(waveform: ShortArray) {
        synchronized(pendingClicksLock) {
            pendingClicks.addLast(waveform)
        }
    }

    private val renderRunnable = object : Runnable {
        override fun run() {
            if (!renderRunning) return

            drainPendingClicks()
            renderBuffer.fill(0)
            mixActiveClicks()
            audioTrack?.write(renderBuffer, 0, renderBuffer.size, AudioTrack.WRITE_BLOCKING)

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
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val fallbackBufferSize = BYTES_PER_SAMPLE * DEFAULT_BUFFER_FRAMES
        val bufferSize = if (minimumBufferSize > 0) {
            minimumBufferSize
        } else {
            fallbackBufferSize
        }
        // Buffer drain time is a lower-bound latency estimate; device HAL latency is not exposed here.
        estimatedOutputLatencyNanos = bytesToFrames(bufferSize) * NANOS_PER_SECOND / SAMPLE_RATE
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
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .build()
        val builder = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        return builder.build().also { audioTrack = it }
    }

    private fun ensureWaveform(soundFile: SoundFile): ShortArray {
        return waveforms.getOrPut(soundFile) { generateWaveform(soundFile) }
    }

    private fun generateWaveform(soundFile: SoundFile): ShortArray {
        val durationMs = when (soundFile) {
            SoundFile.CRASH_L,
            SoundFile.CRASH_R,
            SoundFile.HAT_OPEN,
            SoundFile.RIDE_EDGE,
            SoundFile.RIDE_BELL,
            SoundFile.TAMB -> 90
            SoundFile.KICK,
            SoundFile.SNARE,
            SoundFile.TOM_HI,
            SoundFile.TOM_MID,
            SoundFile.TOM_LO -> 70
            else -> 45
        }
        val waveform = ShortArray(SAMPLE_RATE * durationMs / 1000)
        val profile = WaveformProfile.forSound(soundFile)

        for (i in waveform.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-profile.decay * t)
            val primary = sin(2.0 * PI * profile.frequencyHz * t)
            val harmonic = sin(2.0 * PI * profile.frequencyHz * 1.7 * t) * profile.harmonicMix
            val click = if (i < profile.noiseSamples) {
                deterministicNoise(i, soundFile.ordinal) * profile.noiseMix * (1.0 - i.toDouble() / profile.noiseSamples)
            } else {
                0.0
            }
            val sample = (primary + harmonic + click) * envelope * profile.gain
            waveform[i] = (Short.MAX_VALUE * sample.coerceIn(-1.0, 1.0)).toInt().toShort()
        }

        return waveform
    }

    private fun deterministicNoise(index: Int, seed: Int): Double {
        val value = (index * 1103515245 + seed * 12345) and Int.MAX_VALUE
        return (value / Int.MAX_VALUE.toDouble()) * 2.0 - 1.0
    }

    private data class WaveformProfile(
        val frequencyHz: Double,
        val decay: Double,
        val gain: Double,
        val harmonicMix: Double,
        val noiseMix: Double,
        val noiseSamples: Int
    ) {
        companion object {
            fun forSound(soundFile: SoundFile): WaveformProfile = when (soundFile) {
                SoundFile.CLICK_HI -> WaveformProfile(1600.0, 70.0, 0.70, 0.18, 0.30, 80)
                SoundFile.CLICK_LO -> WaveformProfile(950.0, 62.0, 0.62, 0.16, 0.24, 80)
                SoundFile.COWBELL -> WaveformProfile(720.0, 36.0, 0.65, 0.55, 0.10, 40)
                SoundFile.CRASH_L,
                SoundFile.CRASH_R -> WaveformProfile(3200.0, 26.0, 0.34, 0.70, 0.60, 2200)
                SoundFile.HAT_CLOSED -> WaveformProfile(5200.0, 95.0, 0.34, 0.20, 0.75, 650)
                SoundFile.HAT_OPEN -> WaveformProfile(4200.0, 33.0, 0.30, 0.20, 0.70, 2600)
                SoundFile.KICK -> WaveformProfile(86.0, 42.0, 0.95, 0.08, 0.10, 120)
                SoundFile.RIDE_EDGE -> WaveformProfile(2400.0, 24.0, 0.38, 0.45, 0.36, 900)
                SoundFile.RIDE_BELL -> WaveformProfile(1100.0, 30.0, 0.54, 0.50, 0.12, 80)
                SoundFile.SNARE -> WaveformProfile(210.0, 48.0, 0.55, 0.12, 0.85, 1300)
                SoundFile.TAMB -> WaveformProfile(3800.0, 28.0, 0.36, 0.35, 0.75, 1800)
                SoundFile.TOM_HI -> WaveformProfile(220.0, 30.0, 0.78, 0.18, 0.10, 80)
                SoundFile.TOM_MID -> WaveformProfile(160.0, 28.0, 0.80, 0.18, 0.10, 80)
                SoundFile.TOM_LO -> WaveformProfile(115.0, 26.0, 0.82, 0.18, 0.10, 80)
            }
        }
    }

    private class ActiveClick(
        val waveform: ShortArray,
        var position: Int = 0
    )

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val DEFAULT_BUFFER_FRAMES = 1_024
        const val NANOS_PER_SECOND = 1_000_000_000L
        // Small chunks keep click enqueue-to-render delay low; AudioTrack handles the larger device buffer.
        const val RENDER_CHUNK_FRAMES = 128

        fun bytesToFrames(bytes: Int): Long = (bytes / BYTES_PER_SAMPLE).toLong().coerceAtLeast(1L)
    }
}
