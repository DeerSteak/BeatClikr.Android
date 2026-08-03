package com.bfunkstudios.beatclikr

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import com.bfunkstudios.beatclikr.services.AudioBackendType
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
        try {
            engine.loadSounds(
                requireNotNull(SoundFile.CLICK_HI.resourceId),
                requireNotNull(SoundFile.CLICK_LO.resourceId)
            )
            val session = RenderedEventTestSession.standard(
                engine, TEST_BPM, TEST_SUBDIVISIONS, null, false
            ) { records, sampleRate ->
                records.forEach { event ->
                    if (latch.count > 0L) {
                        scheduledTimes += event.intendedFrame * 1_000_000_000L / sampleRate
                        arrivalTimes += SystemClock.elapsedRealtimeNanos()
                        latch.countDown()
                    }
                }
            }
            logProgress(durationMinutes, latch)
            assertTrue(
                "Timed out with ${latch.count} callbacks missing",
                latch.await(STOP_GRACE_SECONDS, TimeUnit.SECONDS)
            )
            session.close()

            val scheduled = synchronized(scheduledTimes) { scheduledTimes.toList() }
            val arrivals = synchronized(arrivalTimes) { arrivalTimes.toList() }
            val scheduledErrors = intervalErrors(scheduled, expectedIntervalNanos)
            val arrivalErrors = intervalErrors(arrivals, expectedIntervalNanos)
            val expectedSpan = expectedIntervalNanos * (expectedEvents - 1L)
            val scheduledDrift = abs((scheduled.last() - scheduled.first()) - expectedSpan)
            val metrics = requireNotNull(engine.getFrameAudioMetricsSnapshot())

            Log.i(
                TAG,
                "minutes=$durationMinutes events=$expectedEvents " +
                    "scheduledDriftMs=${toMillis(scheduledDrift)} " +
                    "callbackP50Ms=${toMillis(percentile(arrivalErrors, 0.50))} " +
                    "callbackP95Ms=${toMillis(percentile(arrivalErrors, 0.95))} " +
                    "callbackP99Ms=${toMillis(percentile(arrivalErrors, 0.99))} " +
                    "callbackMaxMs=${toMillis(arrivalErrors.max())} " +
                    "backend=${metrics.backend} route=${metrics.route} " +
                    "sampleRate=${metrics.sampleRate} channels=${metrics.channelCount} " +
                    "burstFrames=${metrics.outputFramesPerBuffer} " +
                    "bufferFrames=${metrics.bufferFrames} mode=${metrics.performanceMode} " +
                    "mixP50UpperMs=${toMillis(metrics.mixDurationP50UpperBoundNanos)} " +
                    "mixP95UpperMs=${toMillis(metrics.mixDurationP95UpperBoundNanos)} " +
                    "mixP99UpperMs=${toMillis(metrics.mixDurationP99UpperBoundNanos)} " +
                    "mixMaxMs=${toMillis(metrics.maximumMixDurationNanos)} " +
                    "writeP50UpperMs=${toMillis(metrics.writeDurationP50UpperBoundNanos)} " +
                    "writeP95UpperMs=${toMillis(metrics.writeDurationP95UpperBoundNanos)} " +
                    "writeP99UpperMs=${toMillis(metrics.writeDurationP99UpperBoundNanos)} " +
                    "writeMaxMs=${toMillis(metrics.maximumWriteDurationNanos)} " +
                    "routeChanges=${metrics.routeChangeCount} " +
                    "deadlines=${metrics.deadlineMisses} drops=${metrics.droppedEvents} " +
                    "underruns=${metrics.underrunCount} chunks=${metrics.renderedChunks} " +
                    "intendedFrames=${metrics.intendedFrames} " +
                    "renderedFrames=${metrics.renderedFrames} " +
                    "writtenFrames=${metrics.writtenFrames} " +
                    "presentedFrames=${metrics.estimatedPresentedFrames}"
            )

            assertEquals(expectedEvents, scheduled.size)
            assertEquals(expectedEvents, arrivals.size)
            assertTrue("Scheduled callbacks must increase", scheduled.zipWithNext().all { it.second > it.first })
            assertTrue("Scheduled interval error exceeded 2 ms", scheduledErrors.max() <= 2_000_000L)
            assertTrue("Scheduled drift exceeded 2 ms", scheduledDrift <= 2_000_000L)
            assertTrue("AudioTrack rendered no chunks", metrics.renderedChunks > 0)
            assertTrue("AudioTrack wrote no frames", metrics.writtenFrames > 0)
            assertEquals(AudioBackendType.AUDIO_TRACK, metrics.backend)
            assertTrue("Obtained channel count was invalid", metrics.channelCount > 0)
            assertTrue("Obtained buffer was smaller than its burst", metrics.bufferFrames >= metrics.outputFramesPerBuffer)
            assertEquals(metrics.intendedFrames, metrics.renderedFrames)
            assertEquals(metrics.renderedFrames, metrics.writtenFrames)
            assertTrue("Mix duration diagnostics were empty", metrics.mixDurationP50UpperBoundNanos > 0)
            assertTrue("Write duration diagnostics were empty", metrics.writeDurationP50UpperBoundNanos > 0)
            assertEquals("Render deadline misses occurred", 0, metrics.deadlineMisses)
            assertEquals("Rendered events were dropped", 0, metrics.droppedEvents)
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
