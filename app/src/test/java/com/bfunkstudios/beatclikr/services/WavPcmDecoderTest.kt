package com.bfunkstudios.beatclikr.services

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavPcmDecoderTest {
    @Test
    fun preservesLeadingSilenceAndDownmixesStereo() {
        val wav = wav(
            channels = 2,
            sampleRate = 48_000,
            samples = shortArrayOf(0, 0, 100, 300, -300, -100)
        )

        val result = WavPcmDecoder.decode(wav, 48_000) as WavDecodeResult.Success

        assertArrayEquals(shortArrayOf(0, 200, -200), result.samples)
    }

    @Test
    fun resamplesBeforePublication() {
        val result = WavPcmDecoder.decode(
            wav(1, 24_000, shortArrayOf(0, 1_000, 2_000)),
            48_000
        ) as WavDecodeResult.Success

        assertEquals(6, result.samples.size)
        assertEquals(0, result.samples.first().toInt())
        assertEquals(2_000, result.samples.last().toInt())
    }

    @Test
    fun classifiesCorruptEmptyAndIncompatibleResources() {
        assertEquals(
            SoundPreparationFailureCode.CORRUPT,
            (WavPcmDecoder.decode(byteArrayOf(1, 2), 48_000) as WavDecodeResult.Failure).code
        )
        assertEquals(
            SoundPreparationFailureCode.EMPTY,
            (WavPcmDecoder.decode(wav(1, 48_000, shortArrayOf()), 48_000) as WavDecodeResult.Failure).code
        )
        assertEquals(
            SoundPreparationFailureCode.INCOMPATIBLE,
            (
                WavPcmDecoder.decode(
                    wav(1, 48_000, shortArrayOf(1), audioFormat = 3),
                    48_000
                ) as WavDecodeResult.Failure
                ).code
        )
    }

    companion object {
        fun wav(
            channels: Int,
            sampleRate: Int,
            samples: ShortArray,
            audioFormat: Int = 1
        ): ByteArray {
            val dataBytes = samples.size * 2
            return ByteBuffer.allocate(44 + dataBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    put("RIFF".toByteArray(Charsets.US_ASCII))
                    putInt(36 + dataBytes)
                    put("WAVE".toByteArray(Charsets.US_ASCII))
                    put("fmt ".toByteArray(Charsets.US_ASCII))
                    putInt(16)
                    putShort(audioFormat.toShort())
                    putShort(channels.toShort())
                    putInt(sampleRate)
                    putInt(sampleRate * channels * 2)
                    putShort((channels * 2).toShort())
                    putShort(16)
                    put("data".toByteArray(Charsets.US_ASCII))
                    putInt(dataBytes)
                    samples.forEach(::putShort)
                }
                .array()
        }
    }
}
