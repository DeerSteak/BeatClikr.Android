package com.bfunkstudios.beatclikr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.bfunkstudios.beatclikr.services.FrameAudioMetricsSnapshot
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
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

    @Test
    fun mt005_mt008_allOddMeterPatternsPreserveGroupAccentsInBothTimingUnits() {
        withPreparedAudioEngine(prewarm = true) { engine ->
            AccentContractFixtures.oddMeterSubdivisions.forEach { subdivisions ->
                AccentContractFixtures.oddMeterPatterns.forEach { fixture ->
                    val capture = capture(
                        engine = engine,
                        subdivisions = subdivisions,
                        accentPattern = fixture.accents,
                        alternateSixteenth = false,
                        eventCount = fixture.accents.size
                    )
                    val after = requireNotNull(engine.getFrameAudioMetricsSnapshot())

                    assertEquals("${fixture.pattern}/$subdivisions feedback", fixture.accents, capture.beatFlags)
                    assertIntervals(subdivisions, capture.scheduledTimes)
                    assertSoundRoles(fixture, after)
                }
            }
        }
    }

    @Test
    fun mt009_alternateSixteenthsUseBeatSoundOnEvenTicksAndFeedbackOnTickZero() {
        withPreparedAudioEngine(prewarm = true) { engine ->
            val capture = capture(
                engine = engine,
                subdivisions = 4,
                accentPattern = null,
                alternateSixteenth = true,
                eventCount = ALTERNATE_EVENT_COUNT
            )
            val after = requireNotNull(engine.getFrameAudioMetricsSnapshot())
            val expected = List(ALTERNATE_CYCLE_COUNT) {
                AccentContractFixtures.alternateSixteenthEvents
            }.flatten()

            assertEquals(expected.map { it.isBeat }, capture.beatFlags)
            assertEquals(
                repeatedRoleCount(expected.map { it.soundRole }, after.queuedClicks, ContractSoundRole.BEAT),
                after.queuedBeatClicks
            )
            assertEquals(
                repeatedRoleCount(expected.map { it.soundRole }, after.queuedClicks, ContractSoundRole.RHYTHM),
                after.queuedRhythmClicks
            )
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
        val session = RenderedEventTestSession.standard(
            engine, TEST_BPM, subdivisions, accentPattern, alternateSixteenth
        ) { records, sampleRate ->
            records.forEach { event ->
                if (latch.count > 0L) {
                    scheduledTimes += event.intendedFrame * 1_000_000_000L / sampleRate
                    beatFlags += accentPattern?.getOrNull(event.roleIndex) ?: (event.roleIndex == 0)
                    latch.countDown()
                }
            }
        }
        assertTrue("Timed out waiting for accent contract events", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        awaitFrameAudioMetrics(engine, TIMEOUT_SECONDS * 1_000) { it.queuedClicks >= eventCount }
        session.close()
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

    private fun assertSoundRoles(
        fixture: OddMeterPatternFixture,
        after: FrameAudioMetricsSnapshot
    ) {
        assertTrue(after.queuedClicks >= fixture.soundRoles.size)
        assertEquals(
            "${fixture.pattern} beat sounds",
            repeatedRoleCount(fixture.soundRoles, after.queuedClicks, ContractSoundRole.BEAT),
            after.queuedBeatClicks
        )
        assertEquals(
            "${fixture.pattern} rhythm sounds",
            repeatedRoleCount(fixture.soundRoles, after.queuedClicks, ContractSoundRole.RHYTHM),
            after.queuedRhythmClicks
        )
    }

    private fun repeatedRoleCount(
        roles: List<ContractSoundRole>,
        eventCount: Long,
        target: ContractSoundRole
    ): Long = (0 until eventCount).count { roles[(it % roles.size).toInt()] == target }.toLong()

    private data class EventCapture(
        val scheduledTimes: List<Long>,
        val beatFlags: List<Boolean>
    )

    private companion object {
        const val TEST_BPM = 240f
        const val ALTERNATE_CYCLE_COUNT = 2
        const val ALTERNATE_EVENT_COUNT = 8
        const val TIMEOUT_SECONDS = 6L
        const val INTERVAL_TOLERANCE_NANOS = 100_000L
    }
}
