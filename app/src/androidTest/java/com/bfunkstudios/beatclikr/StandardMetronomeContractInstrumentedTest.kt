package com.bfunkstudios.beatclikr

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StandardMetronomeContractInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mt001_mt003_supportedTempoBoundsAndDecimalBpmAreScheduledWithoutRounding() {
        StandardMetronomeContractFixtures.tempoCases.forEach { fixture ->
            withEngine { engine ->
                val capture = captureEvents(engine, fixture, TEMPO_EVENT_COUNT)
                assertIntervals(fixture, capture.scheduledTimes)
                assertEquals(fixture.events(TEMPO_EVENT_COUNT).map { it.isBeat }, capture.beatFlags)
            }
        }
    }

    @Test
    fun mt004_mt008_standardGroovesStartAtTickZeroAndUseExpectedSoundRoles() {
        StandardMetronomeContractFixtures.grooveCases.forEach { fixture ->
            withEngine { engine ->
                val eventCount = fixture.subdivisions * GROOVE_CYCLE_COUNT
                val capture = captureEvents(engine, fixture, eventCount)
                val expected = fixture.events(eventCount)
                val metrics = requireNotNull(engine.getFrameAudioMetricsSnapshot())
                val rendered = fixture.events(metrics.queuedClicks.toInt())

                assertEquals(expected.map { it.isBeat }, capture.beatFlags)
                assertTrue(metrics.queuedClicks >= eventCount)
                assertEquals(rendered.count { it.soundRole == ContractSoundRole.BEAT }.toLong(), metrics.queuedBeatClicks)
                assertEquals(rendered.count { it.soundRole == ContractSoundRole.RHYTHM }.toLong(), metrics.queuedRhythmClicks)
            }
        }
    }

    @Test
    fun mt010_mutingSuppressesAudioWithoutRemovingEventsOrPhase() {
        val fixture = StandardMetronomeFixture(bpm = 240f, subdivisions = 4)
        withEngine { engine ->
            val scheduledTimes = Collections.synchronizedList(mutableListOf<Long>())
            val beatFlags = Collections.synchronizedList(mutableListOf<Boolean>())
            val eventIndex = AtomicInteger()
            val latch = CountDownLatch(MUTE_EVENT_COUNT)
            val delegate = object : MetronomeTestDelegate() {
                override fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float, beatTimeNanos: Long) {
                    scheduledTimes += beatTimeNanos
                    beatFlags += isBeat
                    when (eventIndex.incrementAndGet()) {
                        MUTE_START_EVENT -> engine.isMuted = true
                        MUTE_END_EVENT -> engine.isMuted = false
                    }
                    latch.countDown()
                }
            }

            engine.startMetronome(fixture.bpm, fixture.subdivisions, null, false, delegate)
            assertTrue("Timed out waiting for mute-continuity events", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            engine.stopMetronome()
            settle()

            val times = synchronized(scheduledTimes) { scheduledTimes.toList() }
            val flags = synchronized(beatFlags) { beatFlags.toList() }
            val metrics = requireNotNull(engine.getFrameAudioMetricsSnapshot())
            val phaseTimes = times.mapIndexed { index, time ->
                if (index < MUTE_START_EVENT || index >= MUTE_END_EVENT) {
                    time - metrics.estimatedOutputLatencyNanos
                } else {
                    time
                }
            }
            assertEquals(fixture.events(MUTE_EVENT_COUNT).map { it.isBeat }, flags)
            assertIntervals(fixture, phaseTimes)
            assertTrue("Audio renderer produced no blocks", metrics.renderedChunks > 0)
        }
    }

    @Test
    fun mt011_mt013_mt014_restartBeginsAtTickZeroWithoutCountInAndStopEndsPhase() {
        val fixture = StandardMetronomeFixture(bpm = 240f, subdivisions = 4)
        withEngine { engine ->
            val firstSessionCallbacks = AtomicInteger()
            val firstSessionLatch = CountDownLatch(fixture.subdivisions)
            val firstSessionDelegate = object : MetronomeTestDelegate() {
                override fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float, beatTimeNanos: Long) {
                    firstSessionCallbacks.incrementAndGet()
                    firstSessionLatch.countDown()
                }
            }
            engine.startMetronome(fixture.bpm, fixture.subdivisions, null, false, firstSessionDelegate)
            assertTrue("Timed out waiting for first session", firstSessionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            engine.stopMetronome()
            settle()
            val callbacksAfterStop = firstSessionCallbacks.get()
            Thread.sleep(STOP_OBSERVATION_MILLIS)
            assertEquals(callbacksAfterStop, firstSessionCallbacks.get())

            val restartRequestNanos = SystemClock.elapsedRealtimeNanos()
            val second = captureEvents(engine, fixture, fixture.subdivisions)
            assertTrue(second.beatFlags.first())
            assertEquals(fixture.events(fixture.subdivisions).map { it.isBeat }, second.beatFlags)
            assertTrue(
                "First restart event behaved like a count-in",
                second.scheduledTimes.first() - restartRequestNanos < NO_COUNT_IN_LIMIT_NANOS
            )
        }
    }

    private fun withEngine(block: (MetronomeAudioEngine) -> Unit) {
        val engine = MetronomeAudioEngine(context)
        try {
            engine.loadSounds(
                requireNotNull(SoundFile.CLICK_HI.resourceId),
                requireNotNull(SoundFile.CLICK_LO.resourceId)
            )
            block(engine)
        } finally {
            engine.release()
        }
    }

    private fun captureEvents(
        engine: MetronomeAudioEngine,
        fixture: StandardMetronomeFixture,
        eventCount: Int
    ): EventCapture {
        val scheduledTimes = Collections.synchronizedList(mutableListOf<Long>())
        val beatFlags = Collections.synchronizedList(mutableListOf<Boolean>())
        val latch = CountDownLatch(eventCount)
        val delegate = object : MetronomeTestDelegate() {
            override fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float, beatTimeNanos: Long) {
                if (latch.count == 0L) return
                scheduledTimes += beatTimeNanos
                beatFlags += isBeat
                latch.countDown()
            }
        }

        engine.startMetronome(fixture.bpm, fixture.subdivisions, null, false, delegate)
        assertTrue("Timed out waiting for contract events", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        awaitRenderedClicks(engine, eventCount)
        engine.stopMetronome()
        settle()
        return EventCapture(
            scheduledTimes = synchronized(scheduledTimes) { scheduledTimes.toList() },
            beatFlags = synchronized(beatFlags) { beatFlags.toList() }
        )
    }

    private fun assertIntervals(fixture: StandardMetronomeFixture, times: List<Long>) {
        times.zipWithNext().forEach { (first, second) ->
            assertTrue(
                "Interval differed from ${fixture.bpm} BPM/${fixture.subdivisions} subdivisions",
                abs((second - first) - fixture.intervalNanos) <= INTERVAL_TOLERANCE_NANOS
            )
        }
    }

    private fun settle() {
        Thread.sleep(STOP_SETTLE_MILLIS)
    }

    private fun awaitRenderedClicks(engine: MetronomeAudioEngine, minimum: Int) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_SECONDS * 1_000
        while (
            requireNotNull(engine.getFrameAudioMetricsSnapshot()).queuedClicks < minimum &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            Thread.sleep(10)
        }
    }

    private data class EventCapture(
        val scheduledTimes: List<Long>,
        val beatFlags: List<Boolean>
    )

    private companion object {
        const val TEMPO_EVENT_COUNT = 5
        const val GROOVE_CYCLE_COUNT = 2
        const val MUTE_EVENT_COUNT = 12
        const val MUTE_START_EVENT = 4
        const val MUTE_END_EVENT = 8
        const val TIMEOUT_SECONDS = 6L
        const val STOP_SETTLE_MILLIS = 150L
        const val STOP_OBSERVATION_MILLIS = 300L
        const val INTERVAL_TOLERANCE_NANOS = 100_000L
        const val NO_COUNT_IN_LIMIT_NANOS = 500_000_000L
    }
}
