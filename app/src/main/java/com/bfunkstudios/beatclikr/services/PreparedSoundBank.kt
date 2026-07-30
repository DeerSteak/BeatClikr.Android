package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import java.util.Collections
import java.util.EnumMap
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicReference

enum class SoundPreparationFailureCode {
    MISSING,
    CORRUPT,
    EMPTY,
    INCOMPATIBLE
}

data class SoundPreparationFailure(
    val bank: SoundBank,
    val sound: SoundFile,
    val code: SoundPreparationFailureCode
)

sealed interface SoundPreparationResult<out T> {
    class Success<T>(val value: T) : SoundPreparationResult<T>
    data class Failure(val failure: SoundPreparationFailure) : SoundPreparationResult<Nothing>
}

class PreparedPcmWaveform(samples: ShortArray) {
    private val storage = samples.copyOf()

    val size: Int
        get() = storage.size

    /** Copies once when publishing a waveform to a renderer or legacy engine. */
    fun copySamples(): ShortArray = storage.copyOf()
}

class PreparedSoundBank private constructor(
    val bank: SoundBank,
    val sampleRate: Int,
    private val waveforms: Map<SoundFile, PreparedPcmWaveform>
) {
    fun waveform(sound: SoundFile): PreparedPcmWaveform? = waveforms[sound]

    fun contains(sound: SoundFile): Boolean = waveforms.containsKey(sound)

    val size: Int
        get() = waveforms.size

    companion object {
        fun create(
            bank: SoundBank,
            sampleRate: Int,
            waveforms: Map<SoundFile, ShortArray>
        ): PreparedSoundBank {
            require(sampleRate > 0) { "Sample rate must be positive" }
            require(waveforms.isNotEmpty()) { "Prepared bank must not be empty" }
            val copied = EnumMap<SoundFile, PreparedPcmWaveform>(SoundFile::class.java)
            waveforms.forEach { (sound, samples) ->
                require(samples.isNotEmpty()) { "Prepared waveform must not be empty" }
                copied[sound] = PreparedPcmWaveform(samples)
            }
            return PreparedSoundBank(
                bank,
                sampleRate,
                Collections.unmodifiableMap(copied)
            )
        }
    }
}

class PreparedSoundBankStore {
    private val banks = AtomicReference<Map<SoundBank, PreparedSoundBank>>(emptyMap())

    fun current(bank: SoundBank): PreparedSoundBank? = banks.get()[bank]

    fun publish(replacement: PreparedSoundBank) {
        while (true) {
            val current = banks.get()
            val updated = current + (replacement.bank to replacement)
            if (banks.compareAndSet(current, updated)) return
        }
    }
}

data class PreparedWaveformCacheKey(
    val version: Int,
    val bank: SoundBank,
    val sound: SoundFile,
    val sampleRate: Int
)

class CachedPreparedWaveform(
    val version: Int,
    val sampleRate: Int,
    val samples: ShortArray
)

sealed interface PreparedWaveformCacheRead {
    data object Missing : PreparedWaveformCacheRead
    data object Corrupt : PreparedWaveformCacheRead
    class Hit(val waveform: CachedPreparedWaveform) : PreparedWaveformCacheRead
}

interface PreparedWaveformCache {
    fun read(key: PreparedWaveformCacheKey): PreparedWaveformCacheRead
    fun write(key: PreparedWaveformCacheKey, waveform: CachedPreparedWaveform)
    fun remove(key: PreparedWaveformCacheKey)
}

fun interface SoundResourceReader {
    fun read(bank: SoundBank, sound: SoundFile): ByteArray?
}

class PreparedSoundRequirements(initial: Collection<SoundFile>) {
    private val sounds = EnumSet.noneOf(SoundFile::class.java).apply { addAll(initial) }

    init {
        require(sounds.isNotEmpty()) { "Required sounds must not be empty" }
    }

    fun include(additional: Collection<SoundFile>) {
        sounds.addAll(additional)
    }

    fun snapshot(): Set<SoundFile> = Collections.unmodifiableSet(EnumSet.copyOf(sounds))
}

class SoundBankPreparer(
    private val sampleRate: Int,
    private val resourceReader: SoundResourceReader,
    private val cache: PreparedWaveformCache,
    private val store: PreparedSoundBankStore
) {
    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
    }

    @Synchronized
    fun prepare(
        bank: SoundBank,
        requiredSounds: Collection<SoundFile>
    ): SoundPreparationResult<PreparedSoundBank> {
        val sounds = requiredSounds.distinct()
        require(sounds.isNotEmpty()) { "Required sounds must not be empty" }
        val prepared = EnumMap<SoundFile, ShortArray>(SoundFile::class.java)
        sounds.forEach { sound ->
            when (val result = prepareWaveform(bank, sound)) {
                is SoundPreparationResult.Success -> prepared[sound] = result.value
                is SoundPreparationResult.Failure -> return result
            }
        }
        val replacement = PreparedSoundBank.create(bank, sampleRate, prepared)
        store.publish(replacement)
        return SoundPreparationResult.Success(replacement)
    }

    private fun prepareWaveform(
        bank: SoundBank,
        sound: SoundFile
    ): SoundPreparationResult<ShortArray> {
        val key = PreparedWaveformCacheKey(CACHE_VERSION, bank, sound, sampleRate)
        val cacheRead = try {
            cache.read(key)
        } catch (_: Exception) {
            PreparedWaveformCacheRead.Corrupt
        }
        val cached = (cacheRead as? PreparedWaveformCacheRead.Hit)?.waveform
        if (
            cached != null &&
            cached.version == CACHE_VERSION &&
            cached.sampleRate == sampleRate &&
            cached.samples.isNotEmpty()
        ) {
            return SoundPreparationResult.Success(cached.samples.copyOf())
        }
        if (cacheRead != PreparedWaveformCacheRead.Missing) {
            try {
                cache.remove(key)
            } catch (_: Exception) {
                // A failed eviction must not prevent rebuilding from the bundled resource.
            }
        }

        val bytes = try {
            resourceReader.read(bank, sound)
        } catch (_: Exception) {
            null
        } ?: return failure(bank, sound, SoundPreparationFailureCode.MISSING)

        return when (val decoded = WavPcmDecoder.decode(bytes, sampleRate)) {
            is WavDecodeResult.Success -> {
                val waveform = CachedPreparedWaveform(
                    version = CACHE_VERSION,
                    sampleRate = sampleRate,
                    samples = decoded.samples.copyOf()
                )
                try {
                    cache.write(key, waveform)
                } catch (_: Exception) {
                    // Generated PCM is optional; the prepared in-memory bank remains valid.
                }
                SoundPreparationResult.Success(decoded.samples)
            }
            is WavDecodeResult.Failure -> failure(bank, sound, decoded.code)
        }
    }

    private fun failure(
        bank: SoundBank,
        sound: SoundFile,
        code: SoundPreparationFailureCode
    ): SoundPreparationResult.Failure =
        SoundPreparationResult.Failure(SoundPreparationFailure(bank, sound, code))

    companion object {
        const val CACHE_VERSION = 4
    }
}
