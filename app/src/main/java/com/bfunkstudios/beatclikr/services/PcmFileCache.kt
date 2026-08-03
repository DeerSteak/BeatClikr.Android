package com.bfunkstudios.beatclikr.services

import android.content.Context
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

class PcmFileCache(
    context: Context,
    val sampleRate: Int
) {
    private val appContext = context.applicationContext
    private val store = PreparedSoundBankStore()
    private val cache = FilePreparedWaveformCache(appContext.filesDir)
    private val preparer = SoundBankPreparer(
        sampleRate = sampleRate,
        resourceReader = SoundResourceReader { bank, sound ->
            val resourceID = sound.resourceIdFor(bank) ?: return@SoundResourceReader null
            appContext.resources.openRawResource(resourceID).use { it.readBytes() }
        },
        cache = cache,
        store = store
    )

    fun prepare(
        soundFiles: Collection<SoundFile>,
        bank: SoundBank
    ): SoundPreparationResult<PreparedSoundBank> = preparer.prepare(bank, soundFiles)

    internal fun preparedBank(bank: SoundBank): PreparedSoundBank? = store.current(bank)

    private class FilePreparedWaveformCache(
        private val filesDir: File
    ) : PreparedWaveformCache {
        private var cleanedOldVersions = false

        override fun read(key: PreparedWaveformCacheKey): PreparedWaveformCacheRead {
            val file = fileFor(key)
            if (!file.exists()) return PreparedWaveformCacheRead.Missing
            return try {
                val bytes = file.readBytes()
                if (bytes.size < HEADER_BYTES) return PreparedWaveformCacheRead.Corrupt
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = buffer.int
                val version = buffer.int
                val sampleRate = buffer.int
                val sampleCount = buffer.int
                if (
                    magic != CACHE_MAGIC ||
                    sampleCount <= 0 ||
                    bytes.size.toLong() != HEADER_BYTES.toLong() + sampleCount.toLong() * 2
                ) {
                    return PreparedWaveformCacheRead.Corrupt
                }
                val samples = ShortArray(sampleCount) { buffer.short }
                PreparedWaveformCacheRead.Hit(
                    CachedPreparedWaveform(version, sampleRate, samples)
                )
            } catch (_: Exception) {
                PreparedWaveformCacheRead.Corrupt
            }
        }

        override fun write(
            key: PreparedWaveformCacheKey,
            waveform: CachedPreparedWaveform
        ) {
            val file = fileFor(key)
            file.parentFile?.mkdirs()
            val bytes = ByteBuffer.allocate(HEADER_BYTES + waveform.samples.size * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(CACHE_MAGIC)
                .putInt(waveform.version)
                .putInt(waveform.sampleRate)
                .putInt(waveform.samples.size)
            waveform.samples.forEach(bytes::putShort)
            val temporary = File.createTempFile(file.name, ".tmp", file.parentFile)
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes.array())
                    output.fd.sync()
                }
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (failure: Exception) {
                temporary.delete()
                throw failure
            }
        }

        override fun remove(key: PreparedWaveformCacheKey) {
            fileFor(key).delete()
        }

        private fun fileFor(key: PreparedWaveformCacheKey): File {
            cleanupOldVersions()
            val baseName = key.sound.fileNameFor(key.bank)
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9_]+"), "_")
            return File(
                File(filesDir, CACHE_DIRECTORY),
                "${key.bank.name.lowercase(Locale.US)}_${baseName}_${key.sampleRate}hz.pcm"
            )
        }

        private fun cleanupOldVersions() {
            if (cleanedOldVersions) return
            GeneratedPcmCacheMaintenance.cleanup(
                filesDir,
                CACHE_PREFIX,
                CACHE_DIRECTORY
            )
            cleanedOldVersions = true
        }

        private companion object {
            const val CACHE_MAGIC = 0x42434B52
            const val HEADER_BYTES = 16
            const val CACHE_PREFIX = "audio_track_pcm_"
            const val CACHE_DIRECTORY = CACHE_PREFIX + "v" + SoundBankPreparer.CACHE_VERSION
        }
    }
}
