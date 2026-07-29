package com.bfunkstudios.beatclikr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.AudioTrackMetricsSnapshot
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
class AccentContractInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mt005_mt008_allOddMeterPatternsPreserveGroupAccentsInBothTimingUnits() {
        withEngine { engine ->
            AccentContractFixtures.oddMeterSubdivisions.forEach { subdivisions ->
                AccentContractFixtures.oddMeterPatterns.forEach { fixture ->
                    val before = requireNotNull(engine.getAudioTrackMetricsSnapshot())
                    val capture = capture(
                        engine = engine,
                        subdivisions = subdivisions,
                        accentPattern = fixture.accents,
                        alternateSixteenth = false,
                        eventCount = fixture.accents.size
                    )
                    val after = requireNotNull(engine.getAudioTrackMetricsSnapshot())

                    assertEquals("${fixture.pattern}/$subdivisions feedback", fixture.accents, capture.beatFlags)
                    assertIntervals(subdivisions, capture.scheduledTimes)
                    assertSoundRoleDeltas(fixture, before, after)
                }
            }
        }
    }

    @Test
    fun mt009_alternateSixteenthsUseBeatSoundOnEvenTicksAndFeedbackOnTickZero() {
        withEngine { engine ->
            val before = requireNotNull(engine.getAudioTrackMetricsSnapshot())
            val capture = capture(
                engine = engine,
                subdivisions = 4,
                accentPattern = null,
                alternateSixteenth = true,
                eventCount = ALTERNATE_EVENT_COUNT
            )
            val after = requireNotNull(engine.getAudioTrackMetricsSnapshot())
            val expected = List(ALTERNATE_CYCLE_COUNT) {
                AccentContractFixtures.alternateSixteenthEvents
            }.flatten()

            assertEquals(expected.map { it.isBeat }, capture.beatFlags)
            assertEquals(
                expected.count { it.soundRole == ContractSoundRole.BEAT }.toLong(),
                after.queuedBeatClicks - before.queuedBeatClicks
            )
            assertEquals(
                expected.count { it.soundRole == ContractSoundRole.RHYTHM }.toLong(),
                after.queuedRhythmClicks - before.queuedRhythmClicks
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
            engine.prewarm()
            Thread.sleep(PREWARM_SETTLE_MILLIS)
            block(engine)
        } finally {
            engine.release()
        }
    }

    private fun capture(
        engine: MetronomeAudioEngine,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean,
        eventCount: Int
    ): EventCapture {
        val scheduledTimes = Collections.synchronizedList(mutableListOf<Long>())
        val beatFlags = Collections.synchronizedList(mutableListOf<Boolean>())
        val latch = CountDownLatch(eventCount)
        val delegate = object : MetronomeAudioEngineDelegate {
            override fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float, beatTimeNanos: Long) {
                if (latch.count == 0L) return
                scheduledTimes += beatTimeNanos
                beatFlags += isBeat
                latch.countDown()
            }
        }

        engine.startMetronome(TEST_BPM, subdivisions, accentPattern, alternateSixteenth, delegate)
        assertTrue("Timed out waiting for accent contract events", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        engine.stopMetronome()
        Thread.sleep(STOP_SETTLE_MILLIS)
        return EventCapture(
            scheduledTimes = synchronized(scheduledTimes) { scheduledTimes.toList() },
            beatFlags = synchronized(beatFlags) { beatFlags.toList() }
        )
    }

    private fun assertIntervals(subdivisions: Int, times: List<Long>) {
        val expected = (60_000_000_000.0 / (TEST_BPM * subdivisions)).toLong()
        times.zipWithNext().forEach { (first, second) ->
            assertTrue(
                "Odd-meter interval differed for subdivisions=$subdivisions",
                abs((second - first) - expected) <= INTERVAL_TOLERANCE_NANOS
            )
        }
    }

    private fun assertSoundRoleDeltas(
        fixture: OddMeterPatternFixture,
        before: AudioTrackMetricsSnapshot,
        after: AudioTrackMetricsSnapshot
    ) {
        assertEquals(
            "${fixture.pattern} beat sounds",
            fixture.soundRoles.count { it == ContractSoundRole.BEAT }.toLong(),
            after.queuedBeatClicks - before.queuedBeatClicks
        )
        assertEquals(
            "${fixture.pattern} rhythm sounds",
            fixture.soundRoles.count { it == ContractSoundRole.RHYTHM }.toLong(),
            after.queuedRhythmClicks - before.queuedRhythmClicks
        )
    }

    private data class EventCapture(
        val scheduledTimes: List<Long>,
        val beatFlags: List<Boolean>
    )

    private companion object {
        const val TEST_BPM = 240f
        const val ALTERNATE_CYCLE_COUNT = 2
        const val ALTERNATE_EVENT_COUNT = 8
        const val TIMEOUT_SECONDS = 6L
        const val PREWARM_SETTLE_MILLIS = 150L
        const val STOP_SETTLE_MILLIS = 100L
        const val INTERVAL_TOLERANCE_NANOS = 100_000L
    }
}
