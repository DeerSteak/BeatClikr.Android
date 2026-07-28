package com.bfunkstudios.beatclikr

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import com.bfunkstudios.beatclikr.services.PcmFileCache
import com.bfunkstudios.beatclikr.services.PolyrhythmAudioEngineDelegate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AudioEngineInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun allProductionSoundsDecodeInBothBanks() {
        val cache = PcmFileCache(context, SAMPLE_RATE)

        SoundBank.entries.forEach { bank ->
            SoundFile.entries.forEach { sound ->
                val waveform = cache.load(sound, bank)
                assertNotNull("${sound.name}/${bank.name} did not decode", waveform)
                assertTrue("${sound.name}/${bank.name} decoded empty", waveform!!.isNotEmpty())
            }
        }
    }

    @Test
    fun denseMetronomeProducesMonotonicCallbacksAndAudio() {
        val engine = MetronomeAudioEngine(context)
        val scheduledTimes = Collections.synchronizedList(mutableListOf<Long>())
        val arrivalTimes = Collections.synchronizedList(mutableListOf<Long>())
        val beatFlags = Collections.synchronizedList(mutableListOf<Boolean>())
        val latch = CountDownLatch(METRONOME_EVENT_COUNT)
        val delegate = object : MetronomeAudioEngineDelegate {
            override fun metronomeBeatFired(
                isBeat: Boolean,
                beatInterval: Float,
                beatTimeNanos: Long
            ) {
                if (latch.count == 0L) return
                scheduledTimes += beatTimeNanos
                arrivalTimes += SystemClock.elapsedRealtimeNanos()
                beatFlags += isBeat
                latch.countDown()
            }
        }

        try {
            engine.loadSounds(requireNotNull(SoundFile.CLICK_HI.resourceId), requireNotNull(SoundFile.CLICK_LO.resourceId))
            engine.startMetronome(
                bpm = MAX_TEST_BPM,
                subdivisions = TEST_SUBDIVISIONS,
                accentPattern = null,
                alternateSixteenth = false,
                delegate = delegate
            )
            assertTrue("Timed out waiting for metronome callbacks", latch.await(10, TimeUnit.SECONDS))
            engine.stopMetronome()

            val scheduled = synchronized(scheduledTimes) { scheduledTimes.toList() }
            val arrivals = synchronized(arrivalTimes) { arrivalTimes.toList() }
            val flags = synchronized(beatFlags) { beatFlags.toList() }
            val expectedInterval = (60_000_000_000.0 / (MAX_TEST_BPM * TEST_SUBDIVISIONS)).toLong()
            val scheduledErrors = intervalErrors(scheduled, expectedInterval)
            val arrivalErrors = intervalErrors(arrivals, expectedInterval)
            val metrics = requireNotNull(engine.getAudioTrackMetricsSnapshot())

            assertEquals(METRONOME_EVENT_COUNT, scheduled.size)
            assertTrue("Scheduled callback times must increase", scheduled.zipWithNext().all { it.second > it.first })
            assertTrue("Scheduled interval error exceeded 2 ms", scheduledErrors.maxOrNull()!! <= 2_000_000L)
            assertTrue("Emulator callback p95 error exceeded 100 ms", percentile(arrivalErrors, 0.95) <= 100_000_000L)
            assertEquals(METRONOME_EVENT_COUNT / TEST_SUBDIVISIONS, flags.count { it })
            assertTrue("No clicks reached the render queue", metrics.queuedClicks >= METRONOME_EVENT_COUNT)
            assertTrue("AudioTrack rendered no chunks", metrics.renderedChunks > 0)
            assertTrue("AudioTrack wrote no frames", metrics.writtenFrames > 0)
            assertTrue("AudioTrack underrun count was invalid", metrics.underrunCount >= 0)

            Log.i(
                TAG,
                "metronome events=${scheduled.size} callbackP95Ms=" +
                    "${percentile(arrivalErrors, 0.95) / 1_000_000.0} " +
                    "underruns=${metrics.underrunCount} chunks=${metrics.renderedChunks}"
            )
        } finally {
            engine.release()
        }
    }

    @Test
    fun polyrhythmProducesBeatRhythmAndCoincidenceEvents() {
        val engine = MetronomeAudioEngine(context)
        val events = Collections.synchronizedList(mutableListOf<Pair<Boolean, Boolean>>())
        val scheduledTimes = Collections.synchronizedList(mutableListOf<Long>())
        val latch = CountDownLatch(POLYRHYTHM_EVENT_COUNT)
        engine.polyrhythmDelegate = object : PolyrhythmAudioEngineDelegate {
            override fun polyrhythmBeatFired(
                beatFired: Boolean,
                rhythmFired: Boolean,
                beatIndex: Int,
                rhythmIndex: Int,
                stepTimeNanos: Long,
                beatDurationNanos: Long,
                rhythmDurationNanos: Long
            ) {
                if (latch.count == 0L) return
                events += beatFired to rhythmFired
                scheduledTimes += stepTimeNanos
                latch.countDown()
            }
        }

        try {
            engine.loadSounds(requireNotNull(SoundFile.CLICK_HI.resourceId), requireNotNull(SoundFile.CLICK_LO.resourceId))
            engine.startPolyrhythm(bpm = MAX_TEST_BPM, beats = 3, against = 2)
            assertTrue("Timed out waiting for polyrhythm callbacks", latch.await(10, TimeUnit.SECONDS))
            engine.stopPolyrhythm()

            val captured = synchronized(events) { events.toList() }
            val times = synchronized(scheduledTimes) { scheduledTimes.toList() }
            assertEquals(POLYRHYTHM_EVENT_COUNT, captured.size)
            assertTrue("Missing beat events", captured.any { it.first })
            assertTrue("Missing rhythm events", captured.any { it.second })
            assertTrue("Missing coincident events", captured.any { it.first && it.second })
            assertTrue("Polyrhythm times must increase", times.zipWithNext().all { it.second > it.first })
        } finally {
            engine.release()
        }
    }

    private fun intervalErrors(times: List<Long>, expectedInterval: Long): List<Long> =
        times.zipWithNext { first, second -> abs((second - first) - expectedInterval) }

    private fun percentile(values: List<Long>, percentile: Double): Long {
        val sorted = values.sorted()
        val index = ((sorted.lastIndex * percentile).toInt()).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private companion object {
        const val TAG = "BeatClikrAudioBaseline"
        const val SAMPLE_RATE = 44_100
        const val MAX_TEST_BPM = 240f
        const val TEST_SUBDIVISIONS = 4
        const val METRONOME_EVENT_COUNT = 48
        const val POLYRHYTHM_EVENT_COUNT = 18
    }
}
