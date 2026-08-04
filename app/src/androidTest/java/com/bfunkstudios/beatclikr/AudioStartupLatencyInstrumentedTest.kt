package com.bfunkstudios.beatclikr

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AudioStartupLatencyInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun coldAndWarmStartsAreMeasured() {
        val cold = List(SAMPLE_COUNT) {
            val engine = MetronomeAudioEngine(context)
            try {
                engine.loadSounds(
                    requireNotNull(SoundFile.CLICK_HI.resourceId),
                    requireNotNull(SoundFile.CLICK_LO.resourceId)
                )
                measureStart(engine)
            } finally {
                engine.release()
            }
        }

        val warmEngine = MetronomeAudioEngine(context)
        val warm = try {
            warmEngine.loadSounds(
                requireNotNull(SoundFile.CLICK_HI.resourceId),
                requireNotNull(SoundFile.CLICK_LO.resourceId)
            )
            List(SAMPLE_COUNT) { measureStart(warmEngine) }
        } finally {
            warmEngine.release()
        }

        logMetrics("cold", cold)
        logMetrics("warm", warm)
        assertBaselineSanity("cold", cold)
        assertBaselineSanity("warm", warm)
    }

    private fun measureStart(engine: MetronomeAudioEngine): Long {
        val firstCallback = AtomicBoolean(true)
        val latch = CountDownLatch(1)
        var firstEventFrame = 0L
        var obtainedSampleRate = 0
        val requestNanos = System.nanoTime()
        val session = RenderedEventTestSession.standard(
            engine, TEST_BPM, TEST_SUBDIVISIONS, null, false
        ) { records, sampleRate ->
            records.forEach { event ->
                if (firstCallback.compareAndSet(true, false)) {
                    firstEventFrame = event.intendedFrame
                    obtainedSampleRate = sampleRate
                    latch.countDown()
                }
            }
        }
        assertTrue("First beat timed out", latch.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val metrics = awaitFrameAudioMetrics(engine) { it.frameCorrelation != null }
        val correlation = requireNotNull(metrics.frameCorrelation)
        assertEquals(obtainedSampleRate, metrics.sampleRate)
        val predictedPresentationNanos = correlation.presentationNanoTime +
            (firstEventFrame - correlation.presentedFrame) * 1_000_000_000L / obtainedSampleRate
        session.close()
        Thread.sleep(STOP_SETTLE_MILLIS)
        return predictedPresentationNanos - requestNanos
    }

    private fun assertBaselineSanity(label: String, values: List<Long>) {
        assertTrue("$label sample count was incomplete", values.size == SAMPLE_COUNT)
        assertTrue("$label startup contained a negative duration", values.min() >= 0L)
        assertTrue("$label p50 exceeded TB-007", percentile(values, 0.50) <= 175_000_000L)
        assertTrue("$label p95 exceeded TB-007", percentile(values, 0.95) <= 225_000_000L)
        assertTrue("$label p99 exceeded TB-007", percentile(values, 0.99) <= 300_000_000L)
    }

    private fun logMetrics(label: String, values: List<Long>) {
        Log.i(
            TAG,
            "type=$label samples=${values.size} " +
                "p50Ms=${toMillis(percentile(values, 0.50))} " +
                "p95Ms=${toMillis(percentile(values, 0.95))} " +
                "p99Ms=${toMillis(percentile(values, 0.99))} " +
                "maxMs=${toMillis(values.max())}"
        )
    }

    private fun percentile(values: List<Long>, percentile: Double): Long {
        val sorted = values.sorted()
        val index = (ceil(sorted.size * percentile).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun toMillis(nanos: Long): Double = nanos / 1_000_000.0

    private companion object {
        const val TAG = "BeatClikrStartup"
        const val SAMPLE_COUNT = 30
        const val START_TIMEOUT_SECONDS = 2L
        const val STOP_SETTLE_MILLIS = 100L
        const val TEST_BPM = 120f
        const val TEST_SUBDIVISIONS = 1
    }
}
