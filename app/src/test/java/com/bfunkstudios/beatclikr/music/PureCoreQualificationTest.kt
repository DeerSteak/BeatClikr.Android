package com.bfunkstudios.beatclikr.music

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PureCoreQualificationTest {

    @Test
    fun tb001_mt003_mt032_twelveHourEndpointIsExactAcrossAudioTrackSampleRateDomain() {
        val intervalsPerMinute = ExactFraction.parseDecimal("412.5")
        val intervalIndex = 297_000L

        for (sampleRate in 4_000..192_000) {
            val timeline = AbsoluteAudioTimeline(sampleRate, intervalsPerMinute)
            val expected = (
                ExactFraction.of(sampleRate.toLong()) *
                    ExactFraction.of(60) /
                    intervalsPerMinute *
                    intervalIndex
                ).roundedLong()

            assertEquals("sampleRate=$sampleRate", expected, timeline.framePosition(intervalIndex))
        }
    }

    @Test
    fun tb001_tb002_mt001_mt032_twelveHourStandardTimelinesStayExactAtEveryBackendRate() {
        val cases = listOf(
            StandardMetronomeConfiguration(
                ExactTempo.of(30),
                StandardTiming.Regular(StandardSubdivision.QUARTER)
            ),
            StandardMetronomeConfiguration(
                ExactTempo.parse("137.5"),
                StandardTiming.Regular(StandardSubdivision.TRIPLET)
            ),
            StandardMetronomeConfiguration(
                ExactTempo.of(240),
                StandardTiming.Regular(StandardSubdivision.SIXTEENTH)
            )
        )

        backendSampleRates.forEach { sampleRate ->
            cases.forEach { configuration ->
                assertTwelveHourStandardTimeline(sampleRate, configuration)
            }
        }
    }

    @Test
    fun tb001_tb002_mt015_mt018_twelveHourDensePolyrhythmStaysExactAtEveryBackendRate() {
        backendSampleRates.forEach { sampleRate ->
            val timeline = PolyrhythmTimeline(
                configuration = PolyrhythmConfiguration(
                    bpm = ExactTempo.of(240),
                    beats = 15,
                    against = 14
                ),
                sampleRate = sampleRate,
                origin = SessionOrigin(SessionID(sampleRate.toLong()), 321)
            )
            val end = 321L + sampleRate * TWELVE_HOURS_SECONDS
            var count = 0L
            var previousFrame = -1L
            var previousSequence = -1L

            timeline.eventsIn(FrameRange(321, end)).forEach { event ->
                assertTrue(event.intendedFrame > previousFrame)
                assertTrue(event.sequence.index > previousSequence)
                previousFrame = event.intendedFrame
                previousSequence = event.sequence.index
                count++
            }

            assertEquals(timeline.eventCountIn(FrameRange(321, end)), count)
            assertTrue(count > 300_000)
        }
    }

    @Test
    fun tb003_mt030_mt031_multiEventStallsRecoverAtEveryStandardEventPosition() {
        StandardSubdivision.entries.forEach { subdivision ->
            val timeline = standardTimeline(subdivision)
            val intervalFrames = 24_000L / subdivision.subdivisions
            repeat(subdivision.subdivisions) { position ->
                val resumeIndex = subdivision.subdivisions + position
                val resumeFrame = resumeIndex * intervalFrames
                val result = DeadlineRecovery.process(
                    timeline,
                    DeadlineRecoveryState.atOrigin(timeline),
                    FrameRange(resumeFrame, resumeFrame + intervalFrames)
                )

                assertEquals(resumeIndex.toLong(), result.state.diagnostics.droppedEvents)
                assertEquals(listOf(resumeIndex.toLong()), result.events.map { it.sequence.index })
                assertTrue(result.events.none { it.intendedFrame < resumeFrame })
            }
        }
    }

    @Test
    fun tb003_mt030_mt031_multiEventStallsRecoverAtEveryPolyrhythmEventPosition() {
        listOf(PolyrhythmConfiguration(ExactTempo.of(120), 3, 2), PolyrhythmConfiguration(ExactTempo.of(120), 5, 7))
            .forEach { configuration ->
                val timeline = PolyrhythmTimeline(
                    configuration,
                    48_000,
                    SessionOrigin(SessionID(configuration.beats.toLong()), 0)
                )
                val cycleEnd = (configuration.cycleDurationSeconds * 48_000L).roundedLong()
                val firstCycle = timeline.eventsIn(FrameRange(0, cycleEnd)).toList()
                firstCycle.indices.forEach { position ->
                    val resume = firstCycle[position].intendedFrame
                    val result = DeadlineRecovery.process(
                        timeline,
                        DeadlineRecoveryState.atOrigin(timeline),
                        FrameRange(resume, resume + 1)
                    )

                    assertEquals(position.toLong(), result.state.diagnostics.droppedEvents)
                    assertEquals(firstCycle[position], result.events.single())
                }
            }
    }

    @Test
    fun tb009_mt011_mt013_mt019_mt020_mt028_everyStandardBoundaryRestartsAtOrigin() {
        val timings = StandardSubdivision.entries.map { StandardTiming.Regular(it) } +
            listOf(
                StandardTiming.Additive(
                    AdditiveStepUnit.QUARTER,
                    AccentPattern.of(listOf(true, false, true, false, false))
                ),
                StandardTiming.Additive(
                    AdditiveStepUnit.EIGHTH,
                    AccentPattern.of(listOf(true, false, false, true, false, true, false))
                )
            )

        timings.forEachIndexed { index, timing ->
            val current = standardSnapshot()
            val command = when (timing) {
                is StandardTiming.Regular -> SetGroove(metadata(index.toLong() + 1), timing.subdivision)
                is StandardTiming.Additive -> SetPattern(metadata(index.toLong() + 1), timing.stepUnit, timing.accents)
            }
            val origin = SessionOrigin(SessionID(index.toLong() + 2), 100_000L + index)
            val result = CommandBoundary.apply(current, listOf(command), origin) as BoundaryResult.Applied
            val mode = result.snapshot.mode as ActivePlaybackMode.Standard
            val first = StandardMetronomeTimeline(mode.configuration, 48_000, origin)
                .eventsIn(FrameRange(origin.originFrame, origin.originFrame + 1))
                .single()

            assertEquals(origin.originFrame, first.intendedFrame)
            assertEquals(0L, first.sequence.index)
            assertTrue(result.restarted)
        }
    }

    @Test
    fun tb010_mt023_seededRandomCommandBatchesRemainAtomicAndSequenced() {
        val random = Random(0xBEA7)
        var snapshot = standardSnapshot()
        var commandSequence = 1L
        var phaseSession = 1L
        val logicalPlaybackID = snapshot.logicalPlaybackID

        repeat(2_000) {
            val batchSize = random.nextInt(1, 6)
            val commands = buildList {
                repeat(batchSize) {
                    add(randomCommand(random, commandSequence++, phaseSession))
                }
            }
            val nextOrigin = SessionOrigin(SessionID(phaseSession + 1), 10_000L + it)
            val prepared = commands
                .filter { command -> command is SetSound || command is SetSoundBank }
                .map { command -> command.metadata.commandSequence }
                .toSet()
            val result = CommandBoundary.apply(snapshot, commands, nextOrigin, prepared) as BoundaryResult.Applied

            assertEquals(commands.last().metadata.commandSequence, result.snapshot.lastCommandSequence)
            assertEquals(logicalPlaybackID, result.snapshot.logicalPlaybackID)
            if (result.restarted) {
                phaseSession++
                assertEquals(SessionID(phaseSession), result.snapshot.origin?.sessionID)
                assertEquals(nextOrigin.originFrame, result.snapshot.origin?.originFrame)
            } else {
                assertEquals(snapshot.origin, result.snapshot.origin)
            }
            snapshot = result.snapshot
        }
    }

    @Test
    fun tb010_mt021_mt023_seededRandomPolyrhythmBatchesRemainAtomicAndSequenced() {
        val random = Random(0xC1C1E)
        var snapshot = polyrhythmSnapshot()
        var commandSequence = 1L
        var phaseSession = 1L

        repeat(1_000) {
            val commands = buildList {
                repeat(random.nextInt(1, 6)) {
                    val metadata = metadata(commandSequence++, phaseSession)
                    add(
                        when (random.nextInt(5)) {
                            0 -> SetTempo(metadata, ExactTempo.of(random.nextInt(30, 241)))
                            1 -> SetPolyrhythm(metadata, random.nextInt(1, 16), random.nextInt(1, 16))
                            2 -> SetSound(metadata, SoundRole.entries.random(random), SoundID("sound-${random.nextInt(15)}"))
                            3 -> SetSoundBank(metadata, SoundBank.entries.random(random))
                            else -> SetMute(metadata, random.nextBoolean())
                        }
                    )
                }
            }
            val nextOrigin = SessionOrigin(SessionID(phaseSession + 1), 50_000L + it)
            val prepared = commands
                .filter { command -> command is SetSound || command is SetSoundBank }
                .map { command -> command.metadata.commandSequence }
                .toSet()
            val result = CommandBoundary.apply(snapshot, commands, nextOrigin, prepared) as BoundaryResult.Applied
            val mode = result.snapshot.mode as ActivePlaybackMode.Polyrhythm

            assertTrue(mode.configuration.beats in PolyrhythmConfiguration.SUPPORTED_COUNT)
            assertTrue(mode.configuration.against in PolyrhythmConfiguration.SUPPORTED_COUNT)
            assertEquals(LogicalPlaybackID(42), result.snapshot.logicalPlaybackID)
            if (result.restarted) phaseSession++
            snapshot = result.snapshot
        }
    }

    @Test
    fun mt014_stopEndsPhaseAndASeparateStartCreatesNewPlaybackIdentity() {
        val current = standardSnapshot()
        val stopped = CommandBoundary.apply(
            current,
            listOf(Stop(metadata(1))),
            SessionOrigin(SessionID(2), 10)
        ) as BoundaryResult.Applied

        assertFalse(stopped.restarted)
        assertEquals(null, stopped.snapshot.mode)
        assertEquals(null, stopped.snapshot.origin)

        val restarted = CommandBoundary.apply(
            stopped.snapshot,
            listOf(
                StartStandard(
                    metadata(0, sessionID = 3),
                    LogicalPlaybackID(99),
                    standardConfiguration(),
                    null
                )
            ),
            SessionOrigin(SessionID(3), 20)
        ) as BoundaryResult.Applied

        assertNotEquals(current.logicalPlaybackID, restarted.snapshot.logicalPlaybackID)
        assertEquals(0L, StandardMetronomeTimeline(
            (restarted.snapshot.mode as ActivePlaybackMode.Standard).configuration,
            48_000,
            requireNotNull(restarted.snapshot.origin)
        ).eventsIn(FrameRange(20, 21)).single().sequence.index)
    }

    private fun assertTwelveHourStandardTimeline(
        sampleRate: Int,
        configuration: StandardMetronomeConfiguration
    ) {
        val origin = 321L
        val timeline = StandardMetronomeTimeline(
            configuration,
            sampleRate,
            SessionOrigin(SessionID(sampleRate.toLong()), origin)
        )
        val end = origin + sampleRate * TWELVE_HOURS_SECONDS
        var count = 0L
        var previousFrame = -1L

        timeline.eventsIn(FrameRange(origin, end)).forEach { event ->
            assertTrue(event.intendedFrame > previousFrame)
            assertEquals(count, event.sequence.index)
            previousFrame = event.intendedFrame
            count++
        }

        assertEquals(timeline.eventCountIn(FrameRange(origin, end)), count)
        assertTrue(count >= 21_600)
    }

    private fun randomCommand(
        random: Random,
        sequence: Long,
        sessionID: Long
    ): PlaybackCommand {
        val metadata = metadata(sequence, sessionID)
        return when (random.nextInt(8)) {
            0 -> SetTempo(metadata, ExactTempo.of(random.nextInt(30, 241)))
            1 -> SetGroove(metadata, StandardSubdivision.entries.random(random))
            2 -> SetPattern(
                metadata,
                AdditiveStepUnit.entries.random(random),
                AccentPattern.of(listOf(true, false, random.nextBoolean(), false, true))
            )
            3 -> SetSound(metadata, SoundRole.entries.random(random), SoundID("sound-${random.nextInt(15)}"))
            4 -> SetSoundBank(metadata, SoundBank.entries.random(random))
            5 -> SetMute(metadata, random.nextBoolean())
            6 -> SetRamp(
                metadata,
                TempoRampConfiguration(
                    TempoRampConfiguration.supportedIncrements.random(random),
                    TempoRampConfiguration.supportedIntervals.random(random)
                )
            )
            else -> SetTempo(metadata, ExactTempo.parse("${random.nextInt(30, 240)}.5"))
        }
    }

    private fun standardTimeline(subdivision: StandardSubdivision) =
        StandardMetronomeTimeline(
            StandardMetronomeConfiguration(
                ExactTempo.of(120),
                StandardTiming.Regular(subdivision)
            ),
            48_000,
            SessionOrigin(SessionID(1), 0)
        )

    private fun standardSnapshot() = PlaybackSnapshot(
        soundConfiguration = SoundConfiguration(
            SoundID("CLICK_HI"),
            SoundID("CLICK_LO"),
            SoundBank.ACOUSTIC
        ),
        logicalPlaybackID = LogicalPlaybackID(42),
        origin = SessionOrigin(SessionID(1), 0),
        mode = ActivePlaybackMode.Standard(standardConfiguration(), null)
    )

    private fun polyrhythmSnapshot() = PlaybackSnapshot(
        soundConfiguration = SoundConfiguration(
            SoundID("CLICK_HI"),
            SoundID("CLICK_LO"),
            SoundBank.ACOUSTIC
        ),
        logicalPlaybackID = LogicalPlaybackID(42),
        origin = SessionOrigin(SessionID(1), 0),
        mode = ActivePlaybackMode.Polyrhythm(
            PolyrhythmConfiguration(ExactTempo.of(120), 3, 2)
        )
    )

    private fun standardConfiguration() = StandardMetronomeConfiguration(
        ExactTempo.of(120),
        StandardTiming.Regular(StandardSubdivision.QUARTER)
    )

    private fun metadata(sequence: Long, sessionID: Long = 1) =
        CommandMetadata(SessionID(sessionID), CommandSequence(sequence), sequence)

    private companion object {
        val backendSampleRates = listOf(44_100, 48_000)
        const val TWELVE_HOURS_SECONDS = 12L * 60 * 60
    }
}
