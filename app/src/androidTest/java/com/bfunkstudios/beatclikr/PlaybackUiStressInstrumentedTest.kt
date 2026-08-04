package com.bfunkstudios.beatclikr

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bfunkstudios.beatclikr.services.BenchmarkDiagnosticsEntryPoint
import com.bfunkstudios.beatclikr.services.BenchmarkPlaybackDiagnostics
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@LargeTest
class PlaybackUiStressInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var diagnostics: BenchmarkPlaybackDiagnostics

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun inject() {
        diagnostics = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BenchmarkDiagnosticsEntryPoint::class.java
        ).diagnostics()
        device.waitForIdle()
    }

    @Test
    fun tb008_tb009_tb010_standardUiUpdatesRemainAtomicWithCorrectRecovery() {
        val durationMinutes = InstrumentationRegistry.getArguments()
            .getString(DURATION_ARGUMENT)?.toIntOrNull()
            ?.coerceIn(1, MAX_DURATION_MINUTES) ?: DEFAULT_DURATION_MINUTES
        setMaximumBpm()
        clickText("subdivision_sixteenth")
        clickText("play")
        waitForPlaying()
        val sessionId = diagnostics.sessionId()
        while (diagnostics.bpm() < MAX_BPM) {
            val nextBpm = diagnostics.bpm() + 1f
            clickDescription("increase_value", "bpm")
            waitForStandardConfiguration(nextBpm, 4, sessionId)
        }
        waitForStandardConfiguration(240f, 4, sessionId)
        val endNanos = SystemClock.elapsedRealtimeNanos() +
            TimeUnit.MINUTES.toNanos(durationMinutes.toLong())
        var iteration = 0

        while (SystemClock.elapsedRealtimeNanos() < endNanos) {
            val cycleStart = SystemClock.elapsedRealtimeNanos()
            clickDescription("decrease_value", "bpm")
            waitForStandardConfiguration(239f, 4, sessionId)
            clickDescription("increase_value", "bpm")
            waitForStandardConfiguration(240f, 4, sessionId)
            clickText("subdivision_eighth")
            waitForStandardConfiguration(240f, 2, sessionId)
            clickText("subdivision_sixteenth")
            waitForStandardConfiguration(240f, 4, sessionId)
            iteration++
            if (iteration % RECREATION_INTERVAL == 0) {
                activityRule.scenario.recreateAndWait()
                waitForStandardConfiguration(240f, 4, sessionId)
                assertTrue(device.hasObject(By.text(string("pause"))))
            }
            val remaining = CYCLE_NANOS - (SystemClock.elapsedRealtimeNanos() - cycleStart)
            if (remaining > 0) SystemClock.sleep(TimeUnit.NANOSECONDS.toMillis(remaining))
        }

        assertEquals(sessionId, diagnostics.sessionId())
        assertTrue("UI stress performed no update cycles", iteration > 0)
        assertTrue("AudioTrack rendered no chunks", diagnostics.renderedChunks() > 0)
        assertEquals(
            diagnostics.intendedFrames(),
            diagnostics.renderedFrames() + diagnostics.underrunSkippedFrames()
        )
        assertEquals(diagnostics.renderedFrames(), diagnostics.writtenFrames())
        assertEquals("Render deadline misses occurred", 0, diagnostics.deadlineMisses())
        assertEquals("Rendered events were dropped", 0, diagnostics.droppedEvents())
        Log.i(
            TAG,
            "minutes=$durationMinutes cycles=$iteration session=$sessionId " +
                "${diagnostics.metricsLog()} " +
                "deadlines=${diagnostics.deadlineMisses()} drops=${diagnostics.droppedEvents()} " +
                "underruns=${diagnostics.underrunCount()} " +
                "underrunSkippedFrames=${diagnostics.underrunSkippedFrames()} " +
                "chunks=${diagnostics.renderedChunks()} " +
                "intendedFrames=${diagnostics.intendedFrames()} " +
                "renderedFrames=${diagnostics.renderedFrames()} " +
                "writtenFrames=${diagnostics.writtenFrames()}"
        )
        clickText("pause")
    }

    private fun setMaximumBpm() {
        val slider = requireNotNull(
            device.wait(Until.findObject(By.desc(string("bpm"))), UI_TIMEOUT_MILLIS)
        )
        val bounds = slider.visibleBounds
        device.swipe(
            bounds.left + bounds.width() / 4,
            bounds.centerY(),
            bounds.right - 2,
            bounds.centerY(),
            SLIDER_SWIPE_STEPS
        )
        device.waitForIdle()
    }

    private fun clickText(resourceName: String) {
        val objectToClick = requireNotNull(
            device.wait(Until.findObject(By.text(string(resourceName))), UI_TIMEOUT_MILLIS)
        )
        objectToClick.click()
        device.waitForIdle()
    }

    private fun clickDescription(formatName: String, valueName: String) {
        val label = string(formatName, string(valueName))
        val objectToClick = requireNotNull(
            device.wait(Until.findObject(By.desc(label)), UI_TIMEOUT_MILLIS)
        )
        objectToClick.click()
        device.waitForIdle()
    }

    private fun waitForPlaying() {
        waitUntil { diagnostics.isPlaying() }
    }

    private fun waitForStandardConfiguration(
        bpm: Float,
        subdivisions: Int,
        sessionId: Long?
    ) {
        waitUntil {
            diagnostics.isPlaying() &&
                (sessionId == null || diagnostics.sessionId() == sessionId) &&
                diagnostics.bpm() == bpm && diagnostics.subdivisions() == subdivisions
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS
        while (!condition() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(
            "Timed out waiting for playback state: playing=${diagnostics.isPlaying()} " +
                "session=${runCatching { diagnostics.sessionId() }.getOrNull()} " +
                "bpm=${runCatching { diagnostics.bpm() }.getOrNull()} " +
                "subdivisions=${runCatching { diagnostics.subdivisions() }.getOrNull()}",
            condition()
        )
    }

    private fun string(name: String, vararg arguments: Any): String {
        val identifier = context.resources.getIdentifier(name, "string", context.packageName)
        require(identifier != 0) { "Missing string resource: $name" }
        return context.getString(identifier, *arguments)
    }

    private fun ActivityScenario<MainActivity>.recreateAndWait() {
        recreate()
        device.waitForIdle()
    }

    private companion object {
        const val DURATION_ARGUMENT = "stressDurationMinutes"
        const val TAG = "BeatClikrUiStress"
        const val DEFAULT_DURATION_MINUTES = 60
        const val MAX_DURATION_MINUTES = 60
        const val MAX_BPM = 240f
        const val RECREATION_INTERVAL = 60
        const val UI_TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
        const val SLIDER_SWIPE_STEPS = 20
        val CYCLE_NANOS = TimeUnit.SECONDS.toNanos(5)
    }
}
