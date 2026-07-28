package com.bfunkstudios.beatclikr

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AudioEngineStressInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun denseMetronomeRemainsStable() {
        val durationMinutes = InstrumentationRegistry.getArguments()
            .getString(DURATION_ARGUMENT)
            ?.toIntOrNull()
            ?.coerceIn(1, MAX_DURATION_MINUTES)
            ?: DEFAULT_DURATION_MINUTES
        val expectedIntervalNanos =
            (60_000_000_000.0 / (TEST_BPM * TEST_SUBDIVISIONS)).toLong()
        val expectedEvents =
            (durationMinutes * 60_000_000_000L / expectedIntervalNanos).toInt()
        val scheduledTimes = Collections.synchronizedList(ArrayList<Long>(expectedEvents))
        val arrivalTimes = Collections.synchronizedList(ArrayList<Long>(expectedEvents))
        val latch = CountDownLatch(expectedEvents)
        val engine = MetronomeAudioEngine(context)
        val delegate = object : MetronomeAudioEngineDelegate {
            override fun metronomeBeatFired(
                isBeat: Boolean,
                beatInterval: Float,
                beatTimeNanos: Long
            ) {
                if (latch.count == 0L) return
                scheduledTimes += beatTimeNanos
                arrivalTimes += SystemClock.elapsedRealtimeNanos()
                latch.countDown()
            }
        }

        try {
            engine.loadSounds(
                requireNotNull(SoundFile.CLICK_HI.resourceId),
                requireNotNull(SoundFile.CLICK_LO.resourceId)
            )
            engine.startMetronome(
                bpm = TEST_BPM,
                subdivisions = TEST_SUBDIVISIONS,
                accentPattern = null,
                alternateSixteenth = false,
                delegate = delegate
            )
            logProgress(durationMinutes, latch)
            assertTrue(
                "Timed out with ${latch.count} callbacks missing",
                latch.await(STOP_GRACE_SECONDS, TimeUnit.SECONDS)
            )
            engine.stopMetronome()

            val scheduled = synchronized(scheduledTimes) { scheduledTimes.toList() }
            val arrivals = synchronized(arrivalTimes) { arrivalTimes.toList() }
            val scheduledErrors = intervalErrors(scheduled, expectedIntervalNanos)
            val arrivalErrors = intervalErrors(arrivals, expectedIntervalNanos)
            val expectedSpan = expectedIntervalNanos * (expectedEvents - 1L)
            val scheduledDrift = abs((scheduled.last() - scheduled.first()) - expectedSpan)
            val metrics = requireNotNull(engine.getAudioTrackMetricsSnapshot())

            Log.i(
                TAG,
                "minutes=$durationMinutes events=$expectedEvents " +
                    "scheduledDriftMs=${toMillis(scheduledDrift)} " +
                    "callbackP50Ms=${toMillis(percentile(arrivalErrors, 0.50))} " +
                    "callbackP95Ms=${toMillis(percentile(arrivalErrors, 0.95))} " +
                    "callbackP99Ms=${toMillis(percentile(arrivalErrors, 0.99))} " +
                    "callbackMaxMs=${toMillis(arrivalErrors.max())} " +
                    "underruns=${metrics.underrunCount} chunks=${metrics.renderedChunks} " +
                    "writtenFrames=${metrics.writtenFrames}"
            )

            assertEquals(expectedEvents, scheduled.size)
            assertEquals(expectedEvents, arrivals.size)
            assertTrue("Scheduled callbacks must increase", scheduled.zipWithNext().all { it.second > it.first })
            assertTrue("Scheduled interval error exceeded 2 ms", scheduledErrors.max() <= 2_000_000L)
            assertTrue("Scheduled drift exceeded 2 ms", scheduledDrift <= 2_000_000L)
            assertTrue("AudioTrack rendered no chunks", metrics.renderedChunks > 0)
            assertTrue("AudioTrack wrote no frames", metrics.writtenFrames > 0)
            assertEquals("AudioTrack underruns occurred", 0, metrics.underrunCount)
        } finally {
            engine.release()
        }
    }

    private fun logProgress(durationMinutes: Int, latch: CountDownLatch) {
        repeat(durationMinutes) { completedMinutes ->
            if (latch.await(1, TimeUnit.MINUTES)) return
            Log.i(
                TAG,
                "progressMinutes=${completedMinutes + 1}/$durationMinutes remainingEvents=${latch.count}"
            )
        }
    }

    private fun intervalErrors(times: List<Long>, expectedInterval: Long): List<Long> =
        times.zipWithNext { first, second -> abs((second - first) - expectedInterval) }

    private fun percentile(values: List<Long>, percentile: Double): Long {
        val sorted = values.sorted()
        val index = (sorted.lastIndex * percentile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun toMillis(nanos: Long): Double = nanos / 1_000_000.0

    private companion object {
        const val TAG = "BeatClikrAudioStress"
        const val DURATION_ARGUMENT = "stressDurationMinutes"
        const val DEFAULT_DURATION_MINUTES = 30
        const val MAX_DURATION_MINUTES = 60
        const val STOP_GRACE_SECONDS = 30L
        const val TEST_BPM = 240f
        const val TEST_SUBDIVISIONS = 4
    }
}
