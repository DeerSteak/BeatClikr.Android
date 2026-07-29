package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PreparedSoundBankTest {
    @Test
    fun publishesCompleteImmutableReplacementAtomically() {
        val store = PreparedSoundBankStore()
        val reader = MapReader(
            mutableMapOf(
                key(SoundBank.ACOUSTIC, SoundFile.CLICK_HI) to wav(shortArrayOf(1, 2)),
                key(SoundBank.ACOUSTIC, SoundFile.CLICK_LO) to wav(shortArrayOf(3, 4))
            )
        )
        val preparer = preparer(reader, MemoryCache(), store)

        val first = success(
            preparer.prepare(SoundBank.ACOUSTIC, listOf(SoundFile.CLICK_HI))
        )
        val sourceCopy = first.waveform(SoundFile.CLICK_HI)!!.copySamples()
        sourceCopy[0] = 99
        val replacement = success(
            preparer.prepare(
                SoundBank.ACOUSTIC,
                listOf(SoundFile.CLICK_HI, SoundFile.CLICK_LO)
            )
        )

        assertSame(replacement, store.current(SoundBank.ACOUSTIC))
        assertEquals(2, replacement.size)
        assertArrayEquals(
            shortArrayOf(1, 2),
            replacement.waveform(SoundFile.CLICK_HI)!!.copySamples()
        )
        assertNotSame(first, replacement)
    }

    @Test
    fun failedReplacementLeavesPublishedBankUntouched() {
        val store = PreparedSoundBankStore()
        val reader = MapReader(
            mutableMapOf(
                key(SoundBank.ACOUSTIC, SoundFile.CLICK_HI) to wav(shortArrayOf(1))
            )
        )
        val preparer = preparer(reader, MemoryCache(), store)
        val published = success(
            preparer.prepare(SoundBank.ACOUSTIC, listOf(SoundFile.CLICK_HI))
        )

        val failure = preparer.prepare(
            SoundBank.ACOUSTIC,
            listOf(SoundFile.CLICK_HI, SoundFile.CLICK_LO)
        ) as SoundPreparationResult.Failure

        assertEquals(SoundPreparationFailureCode.MISSING, failure.failure.code)
        assertSame(published, store.current(SoundBank.ACOUSTIC))
    }

    @Test
    fun bankSwitchingKeepsIndependentAtomicSnapshots() {
        val store = PreparedSoundBankStore()
        val reader = MapReader(
            mutableMapOf(
                key(SoundBank.ACOUSTIC, SoundFile.CLICK_HI) to wav(shortArrayOf(10)),
                key(SoundBank.SYNTH, SoundFile.CLICK_HI) to wav(shortArrayOf(20))
            )
        )
        val preparer = preparer(reader, MemoryCache(), store)

        val acoustic = success(preparer.prepare(SoundBank.ACOUSTIC, listOf(SoundFile.CLICK_HI)))
        val synth = success(preparer.prepare(SoundBank.SYNTH, listOf(SoundFile.CLICK_HI)))

        assertSame(acoustic, store.current(SoundBank.ACOUSTIC))
        assertSame(synth, store.current(SoundBank.SYNTH))
        assertArrayEquals(shortArrayOf(10), acoustic.waveform(SoundFile.CLICK_HI)!!.copySamples())
        assertArrayEquals(shortArrayOf(20), synth.waveform(SoundFile.CLICK_HI)!!.copySamples())
    }

    @Test
    fun staleCacheVersionIsRebuiltFromResource() {
        val store = PreparedSoundBankStore()
        val cache = MemoryCache()
        val reader = MapReader(
            mutableMapOf(
                key(SoundBank.ACOUSTIC, SoundFile.CLICK_HI) to wav(shortArrayOf(7, 8))
            )
        )
        val cacheKey = PreparedWaveformCacheKey(
            SoundBankPreparer.CACHE_VERSION,
            SoundBank.ACOUSTIC,
            SoundFile.CLICK_HI,
            48_000
        )
        cache.values[cacheKey] = CachedPreparedWaveform(
            version = SoundBankPreparer.CACHE_VERSION - 1,
            sampleRate = 48_000,
            samples = shortArrayOf()
        )

        val bank = success(preparer(reader, cache, store).prepare(
            SoundBank.ACOUSTIC,
            listOf(SoundFile.CLICK_HI)
        ))

        assertEquals(1, cache.removes)
        assertEquals(1, cache.writes)
        assertArrayEquals(shortArrayOf(7, 8), bank.waveform(SoundFile.CLICK_HI)!!.copySamples())
    }

    @Test
    fun corruptCacheReadIsRebuiltFromResource() {
        val cache = MemoryCache().apply { failReads = true }
        val reader = MapReader(
            mutableMapOf(
                key(SoundBank.ACOUSTIC, SoundFile.CLICK_HI) to wav(shortArrayOf(5, 6))
            )
        )

        val bank = success(
            preparer(reader, cache, PreparedSoundBankStore()).prepare(
                SoundBank.ACOUSTIC,
                listOf(SoundFile.CLICK_HI)
            )
        )

        assertEquals(1, cache.writes)
        assertArrayEquals(shortArrayOf(5, 6), bank.waveform(SoundFile.CLICK_HI)!!.copySamples())
    }

    @Test
    fun corruptEmptyAndIncompatibleRequiredSoundsReturnTypedFailures() {
        val cases = listOf(
            byteArrayOf(1) to SoundPreparationFailureCode.CORRUPT,
            WavPcmDecoderTest.wav(1, 48_000, shortArrayOf()) to SoundPreparationFailureCode.EMPTY,
            WavPcmDecoderTest.wav(1, 48_000, shortArrayOf(1), audioFormat = 3) to
                SoundPreparationFailureCode.INCOMPATIBLE
        )
        cases.forEach { (bytes, expected) ->
            val preparer = preparer(
                MapReader(mutableMapOf(key(SoundBank.ACOUSTIC, SoundFile.CLICK_HI) to bytes)),
                MemoryCache(),
                PreparedSoundBankStore()
            )

            val failure = preparer.prepare(
                SoundBank.ACOUSTIC,
                listOf(SoundFile.CLICK_HI)
            ) as SoundPreparationResult.Failure

            assertEquals(expected, failure.failure.code)
        }
    }

    @Test
    fun concurrentPreparationDecodesRequiredResourceOnce() {
        val store = PreparedSoundBankStore()
        val cache = MemoryCache()
        val reader = MapReader(
            mutableMapOf(
                key(SoundBank.ACOUSTIC, SoundFile.CLICK_HI) to wav(shortArrayOf(1))
            )
        )
        val preparer = preparer(reader, cache, store)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        val futures = List(4) {
            executor.submit<SoundPreparationResult<PreparedSoundBank>> {
                start.await()
                preparer.prepare(SoundBank.ACOUSTIC, listOf(SoundFile.CLICK_HI))
            }
        }

        start.countDown()
        futures.forEach { success(it.get()) }
        executor.shutdown()

        assertEquals(1, reader.reads)
        assertEquals(1, cache.writes)
    }

    @Test
    fun noBankIsPublishedBeforeSuccessfulPreparation() {
        val store = PreparedSoundBankStore()
        val preparer = preparer(MapReader(mutableMapOf()), MemoryCache(), store)

        preparer.prepare(SoundBank.SYNTH, listOf(SoundFile.CLICK_HI))

        assertNull(store.current(SoundBank.SYNTH))
    }

    private fun preparer(
        reader: SoundResourceReader,
        cache: PreparedWaveformCache,
        store: PreparedSoundBankStore
    ) = SoundBankPreparer(48_000, reader, cache, store)

    private fun success(
        result: SoundPreparationResult<PreparedSoundBank>
    ): PreparedSoundBank = (result as SoundPreparationResult.Success).value

    private fun key(bank: SoundBank, sound: SoundFile) = bank to sound

    private fun wav(samples: ShortArray) = WavPcmDecoderTest.wav(1, 48_000, samples)

    private class MapReader(
        private val values: MutableMap<Pair<SoundBank, SoundFile>, ByteArray>
    ) : SoundResourceReader {
        var reads = 0

        override fun read(bank: SoundBank, sound: SoundFile): ByteArray? {
            reads++
            return values[bank to sound]
        }
    }

    private class MemoryCache : PreparedWaveformCache {
        val values = mutableMapOf<PreparedWaveformCacheKey, CachedPreparedWaveform>()
        var writes = 0
        var removes = 0
        var failReads = false

        override fun read(key: PreparedWaveformCacheKey): CachedPreparedWaveform? {
            if (failReads) error("Corrupt cache")
            return values[key]
        }

        override fun write(
            key: PreparedWaveformCacheKey,
            waveform: CachedPreparedWaveform
        ) {
            writes++
            values[key] = waveform
        }

        override fun remove(key: PreparedWaveformCacheKey) {
            removes++
            values.remove(key)
        }
    }
}
