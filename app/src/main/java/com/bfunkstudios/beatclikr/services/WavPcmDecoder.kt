package com.bfunkstudios.beatclikr.services

sealed interface WavDecodeResult {
    data class Success(val samples: ShortArray) : WavDecodeResult
    data class Failure(val code: SoundPreparationFailureCode) : WavDecodeResult
}

object WavPcmDecoder {
    fun decode(bytes: ByteArray, targetSampleRate: Int): WavDecodeResult {
        if (targetSampleRate <= 0) {
            return WavDecodeResult.Failure(SoundPreparationFailureCode.INCOMPATIBLE)
        }
        val wav = parse(bytes) ?: return WavDecodeResult.Failure(SoundPreparationFailureCode.CORRUPT)
        if (wav.audioFormat != PCM_FORMAT || wav.bitsPerSample != BITS_PER_SAMPLE) {
            return WavDecodeResult.Failure(SoundPreparationFailureCode.INCOMPATIBLE)
        }
        if (wav.channels <= 0 || wav.sampleRate <= 0 || wav.dataSize % (wav.channels * 2) != 0) {
            return WavDecodeResult.Failure(SoundPreparationFailureCode.CORRUPT)
        }
        val mono = downmix(bytes, wav)
        if (mono.isEmpty()) return WavDecodeResult.Failure(SoundPreparationFailureCode.EMPTY)
        return WavDecodeResult.Success(resample(mono, wav.sampleRate, targetSampleRate))
    }

    private fun parse(bytes: ByteArray): WavFormat? {
        if (bytes.size < MINIMUM_HEADER_SIZE) return null
        if (bytes.ascii(0, 4) != "RIFF" || bytes.ascii(8, 4) != "WAVE") return null
        var offset = 12
        var audioFormat = -1
        var channels = -1
        var sampleRate = -1
        var bitsPerSample = -1
        var dataStart = -1
        var dataSize = -1
        while (offset <= bytes.size - 8) {
            val chunkSize = bytes.intLe(offset + 4)
            if (chunkSize < 0) return null
            val chunkStart = offset + 8
            val chunkEnd = chunkStart.toLong() + chunkSize
            if (chunkEnd > bytes.size) return null
            when (bytes.ascii(offset, 4)) {
                "fmt " -> {
                    if (chunkSize < 16) return null
                    audioFormat = bytes.unsignedShortLe(chunkStart)
                    channels = bytes.unsignedShortLe(chunkStart + 2)
                    sampleRate = bytes.intLe(chunkStart + 4)
                    bitsPerSample = bytes.unsignedShortLe(chunkStart + 14)
                }
                "data" -> {
                    dataStart = chunkStart
                    dataSize = chunkSize
                }
            }
            offset = (chunkEnd + (chunkSize and 1)).toInt()
        }
        if (dataStart < 0 || dataSize < 0 || audioFormat < 0) return null
        return WavFormat(audioFormat, channels, sampleRate, bitsPerSample, dataStart, dataSize)
    }

    private fun downmix(bytes: ByteArray, wav: WavFormat): ShortArray {
        val bytesPerFrame = wav.channels * 2
        val frameCount = wav.dataSize / bytesPerFrame
        return ShortArray(frameCount) { frame ->
            var sum = 0L
            val frameStart = wav.dataStart + frame * bytesPerFrame
            var channel = 0
            while (channel < wav.channels) {
                sum += bytes.shortLe(frameStart + channel * 2)
                channel++
            }
            (sum / wav.channels).toShort()
        }
    }

    private fun resample(
        source: ShortArray,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): ShortArray {
        if (sourceSampleRate == targetSampleRate) return source
        val targetSize = (source.size.toLong() * targetSampleRate / sourceSampleRate)
            .toInt()
            .coerceAtLeast(1)
        return ShortArray(targetSize) { index ->
            val position = index.toDouble() * sourceSampleRate / targetSampleRate
            val left = position.toInt().coerceIn(0, source.lastIndex)
            val right = (left + 1).coerceAtMost(source.lastIndex)
            val fraction = position - left
            (source[left] * (1.0 - fraction) + source[right] * fraction)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private data class WavFormat(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataStart: Int,
        val dataSize: Int
    )

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.unsignedShortLe(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.shortLe(offset: Int): Short = unsignedShortLe(offset).toShort()

    private fun ByteArray.intLe(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private const val MINIMUM_HEADER_SIZE = 44
    private const val PCM_FORMAT = 1
    private const val BITS_PER_SAMPLE = 16
}
