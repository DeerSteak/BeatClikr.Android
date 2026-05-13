package com.bfunkstudios.beatclikr.services

import android.content.Context
import android.util.Log
import com.bfunkstudios.beatclikr.data.SoundFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class PcmFileCache(
    context: Context,
    val sampleRate: Int
) {
    private val appContext = context.applicationContext
    private var cleanedOldVersions = false

    fun prepare(soundFiles: Collection<SoundFile>) {
        soundFiles.distinct().forEach(::load)
    }

    @Synchronized
    fun load(soundFile: SoundFile): ShortArray? {
        val resourceId = soundFile.resourceId ?: return null
        val cacheFile = cacheFileFor(soundFile)
        return runCatching {
            if (!cacheFile.exists()) {
                cacheFile.parentFile?.mkdirs()
                writeCachedWaveform(cacheFile, decodeResourceWaveform(resourceId))
            }
            readCachedWaveform(cacheFile)
        }.onFailure { error ->
            Log.w(TAG, "Unable to prepare cached PCM for ${soundFile.name}; using synthetic fallback.", error)
        }.getOrNull()
    }

    private fun cacheFileFor(soundFile: SoundFile): File {
        val fileName = soundFile.fileName
            // Defensively sanitize future file names before using them as cache entries.
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_")
        return File(audioCacheDir(), "${fileName}_${sampleRate}hz_mono16.pcm")
    }

    private fun audioCacheDir(): File {
        cleanupOldVersionsIfNeeded()
        // Use filesDir rather than cacheDir so OS eviction cannot cause a decode spike mid-session.
        return File(appContext.filesDir, AUDIO_CACHE_VERSION)
    }

    private fun cleanupOldVersionsIfNeeded() {
        if (cleanedOldVersions) return
        appContext.filesDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(AUDIO_CACHE_PREFIX) && it.name != AUDIO_CACHE_VERSION }
            ?.forEach { it.deleteRecursively() }
        cleanedOldVersions = true
    }

    private fun writeCachedWaveform(file: File, waveform: ShortArray) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        val bytes = ByteBuffer.allocate(waveform.size * BYTES_PER_SAMPLE)
            .order(ByteOrder.LITTLE_ENDIAN)
        waveform.forEach { bytes.putShort(it) }
        tempFile.writeBytes(bytes.array())
        if (!tempFile.renameTo(file)) {
            file.writeBytes(bytes.array())
            tempFile.delete()
        }
    }

    private fun readCachedWaveform(file: File): ShortArray {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(bytes.size / BYTES_PER_SAMPLE) { buffer.short }
    }

    private fun decodeResourceWaveform(resourceId: Int): ShortArray {
        val bytes = appContext.resources.openRawResource(resourceId).use { it.readBytes() }
        val wav = parseWav(bytes)
        return applyFadeIn(trimLeadingSilence(resampleIfNeeded(downmixToMono(wav), wav.sampleRate)))
    }

    private fun parseWav(bytes: ByteArray): WavData {
        require(bytes.size >= WAV_HEADER_SIZE)
        require(bytes.ascii(0, 4) == "RIFF")
        require(bytes.ascii(8, 4) == "WAVE")

        var offset = 12
        var channels = 0
        var sourceSampleRate = 0
        var bitsPerSample = 0
        var audioFormat = 0
        var dataStart = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.ascii(offset, 4)
            val chunkSize = bytes.intLe(offset + 4)
            val chunkStart = offset + 8
            val chunkEnd = chunkStart + chunkSize
            if (chunkEnd > bytes.size) break

            when (chunkId) {
                "fmt " -> {
                    audioFormat = bytes.shortLe(chunkStart).toInt()
                    channels = bytes.shortLe(chunkStart + 2).toInt()
                    sourceSampleRate = bytes.intLe(chunkStart + 4)
                    bitsPerSample = bytes.shortLe(chunkStart + 14).toInt()
                }
                "data" -> {
                    dataStart = chunkStart
                    dataSize = chunkSize
                }
            }

            offset = chunkEnd + (chunkSize and 1)
        }

        require(audioFormat == WAV_FORMAT_PCM)
        require(channels > 0)
        require(sourceSampleRate > 0)
        require(bitsPerSample == 16)
        require(dataStart >= 0 && dataSize > 0)
        return WavData(
            bytes = bytes,
            dataStart = dataStart,
            dataSize = dataSize,
            channels = channels,
            sampleRate = sourceSampleRate
        )
    }

    private fun downmixToMono(wav: WavData): ShortArray {
        val bytesPerFrame = wav.channels * BYTES_PER_SAMPLE
        val frameCount = wav.dataSize / bytesPerFrame
        return ShortArray(frameCount) { frameIndex ->
            var sum = 0
            val frameStart = wav.dataStart + frameIndex * bytesPerFrame
            repeat(wav.channels) { channel ->
                sum += wav.bytes.shortLe(frameStart + channel * BYTES_PER_SAMPLE)
            }
            (sum / wav.channels).toShort()
        }
    }

    private fun resampleIfNeeded(source: ShortArray, sourceSampleRate: Int): ShortArray {
        if (sourceSampleRate == sampleRate || source.isEmpty()) return source

        val targetSize = (source.size.toLong() * sampleRate / sourceSampleRate).toInt().coerceAtLeast(1)
        return ShortArray(targetSize) { index ->
            val sourcePosition = index.toDouble() * sourceSampleRate / sampleRate
            val leftIndex = sourcePosition.toInt().coerceIn(0, source.lastIndex)
            val rightIndex = (leftIndex + 1).coerceAtMost(source.lastIndex)
            val fraction = sourcePosition - leftIndex
            val sample = source[leftIndex] * (1.0 - fraction) + source[rightIndex] * fraction
            sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun trimLeadingSilence(source: ShortArray): ShortArray {
        val firstAudibleSample = source.indexOfFirst { sample ->
            kotlin.math.abs(sample.toInt()) > LEADING_SILENCE_THRESHOLD
        }
        return when (firstAudibleSample) {
            -1, 0 -> source
            else -> source.copyOfRange(firstAudibleSample, source.size)
        }
    }

    private fun applyFadeIn(source: ShortArray): ShortArray {
        if (source.isEmpty()) return source

        val fadeSampleCount = (sampleRate * FADE_IN_DURATION_MS / 1_000)
            .coerceIn(1, source.size)
        return source.copyOf().also { faded ->
            for (index in 0 until fadeSampleCount) {
                val gain = index.toDouble() / fadeSampleCount
                faded[index] = (faded[index] * gain)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
    }

    private class WavData(
        val bytes: ByteArray,
        val dataStart: Int,
        val dataSize: Int,
        val channels: Int,
        val sampleRate: Int
    )

    private companion object {
        const val TAG = "PcmFileCache"
        const val AUDIO_CACHE_PREFIX = "audio_track_pcm_"
        const val AUDIO_CACHE_VERSION = "audio_track_pcm_v3"
        const val BYTES_PER_SAMPLE = 2
        const val FADE_IN_DURATION_MS = 1
        const val LEADING_SILENCE_THRESHOLD = 32
        const val WAV_HEADER_SIZE = 44
        const val WAV_FORMAT_PCM = 1

        fun ByteArray.ascii(offset: Int, length: Int): String =
            String(this, offset, length, Charsets.US_ASCII)

        fun ByteArray.shortLe(offset: Int): Short =
            ((this[offset].toInt() and 0xff) or
                ((this[offset + 1].toInt() and 0xff) shl 8)).toShort()

        fun ByteArray.intLe(offset: Int): Int =
            (this[offset].toInt() and 0xff) or
                ((this[offset + 1].toInt() and 0xff) shl 8) or
                ((this[offset + 2].toInt() and 0xff) shl 16) or
                ((this[offset + 3].toInt() and 0xff) shl 24)
    }
}
