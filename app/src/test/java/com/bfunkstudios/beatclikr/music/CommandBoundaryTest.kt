package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandBoundaryTest {

    @Test
    fun mt019_mt020_mt023_sameBoundaryCommandsApplyInSequenceAndRestartAtTickZero() {
        val current = standardSnapshot()
        val commands = listOf(
            SetTempo(metadata(1), ExactTempo.parse("137.5")),
            SetGroove(metadata(2), StandardSubdivision.TRIPLET),
            SetPattern(
                metadata(3),
                AdditiveStepUnit.EIGHTH,
                AccentPattern.of(listOf(true, false, false, true, false))
            ),
            SetRamp(metadata(4), TempoRampConfiguration(5, 8))
        )

        val result = applied(
            CommandBoundary.apply(current, commands, SessionOrigin(SessionID(2), 96_000))
        )
        val mode = result.snapshot.mode as ActivePlaybackMode.Standard
        val first = StandardMetronomeTimeline(
            mode.configuration,
            48_000,
            requireNotNull(result.snapshot.origin)
        ).eventsIn(FrameRange(96_000, 96_001)).single()

        assertTrue(result.restarted)
        assertEquals(ExactTempo.parse("137.5"), mode.configuration.bpm)
        assertTrue(mode.configuration.timing is StandardTiming.Additive)
        assertEquals(TempoRampConfiguration(5, 8), mode.ramp)
        assertEquals(0L, first.sequence.index)
        assertEquals(BeatIdentity.ACCENT, first.primary.beatIdentity)
        assertEquals(LogicalPlaybackID(44), result.snapshot.logicalPlaybackID)
    }

    @Test
    fun mt021_polyrhythmTempoAndRatioRestartAtOneSharedOrigin() {
        val current = polyrhythmSnapshot()
        val commands = listOf(
            SetTempo(metadata(1), ExactTempo.of(144)),
            SetPolyrhythm(metadata(2), beats = 5, against = 7)
        )

        val result = applied(
            CommandBoundary.apply(current, commands, SessionOrigin(SessionID(2), 200_000))
        )
        val mode = result.snapshot.mode as ActivePlaybackMode.Polyrhythm
        val first = PolyrhythmTimeline(
            mode.configuration,
            48_000,
            requireNotNull(result.snapshot.origin)
        ).eventsIn(FrameRange(200_000, 200_001)).single()

        assertTrue(result.restarted)
        assertEquals(ExactTempo.of(144), mode.configuration.bpm)
        assertEquals(5, mode.configuration.beats)
        assertEquals(7, mode.configuration.against)
        assertEquals(MusicalEventRole.POLYRHYTHM_BEAT, first.primary.role)
        assertEquals(MusicalEventRole.POLYRHYTHM_RHYTHM, first.secondary?.role)
        assertEquals(LogicalPlaybackID(44), result.snapshot.logicalPlaybackID)
    }

    @Test
    fun mt022_soundChangesRequirePreparationBeforeAtomicPublication() {
        val current = standardSnapshot()
        val commands = listOf(
            SetSound(metadata(1), SoundRole.BEAT, SoundID("COWBELL")),
            SetSoundBank(metadata(2), SoundBank.SYNTH)
        )

        val waiting = CommandBoundary.apply(
            current,
            commands,
            SessionOrigin(SessionID(2), 50_000)
        ) as BoundaryResult.PreparationRequired

        assertEquals(setOf(CommandSequence(1), CommandSequence(2)), waiting.commandSequences)
        assertEquals(SoundID("CLICK_HI"), current.soundConfiguration.beatSound)
        assertEquals(SoundBank.ACOUSTIC, current.soundConfiguration.soundBank)

        val published = applied(
            CommandBoundary.apply(
                current,
                commands,
                SessionOrigin(SessionID(2), 50_000),
                preparedSoundCommands = waiting.commandSequences
            )
        )

        assertEquals(SoundID("COWBELL"), published.snapshot.soundConfiguration.beatSound)
        assertEquals(SoundBank.SYNTH, published.snapshot.soundConfiguration.soundBank)
        assertEquals(50_000L, published.snapshot.origin?.originFrame)
    }

    @Test
    fun mt022_muteWaitsForTheNextStartOrRestart() {
        val current = standardSnapshot()
        val muteOnly = applied(
            CommandBoundary.apply(
                current,
                listOf(SetMute(metadata(1), true)),
                SessionOrigin(SessionID(2), 50_000)
            )
        )
        val unchangedMode = muteOnly.snapshot.mode as ActivePlaybackMode.Standard

        assertFalse(muteOnly.restarted)
        assertFalse(unchangedMode.configuration.muteMetronome)
        assertTrue(muteOnly.snapshot.requestedMute)

        val restarted = applied(
            CommandBoundary.apply(
                muteOnly.snapshot,
                listOf(SetTempo(metadata(2, sessionID = 1), ExactTempo.of(130))),
                SessionOrigin(SessionID(2), 75_000)
            )
        )
        val restartedMode = restarted.snapshot.mode as ActivePlaybackMode.Standard

        assertTrue(restartedMode.configuration.muteMetronome)
    }

    @Test
    fun mt023_finalSameBoundaryValueWinsWithoutIntermediatePublication() {
        val current = standardSnapshot()
        val result = applied(
            CommandBoundary.apply(
                current,
                listOf(
                    SetTempo(metadata(1), ExactTempo.of(100)),
                    SetTempo(metadata(2), ExactTempo.of(180)),
                    SetTempo(metadata(3), ExactTempo.of(120))
                ),
                SessionOrigin(SessionID(2), 80_000)
            )
        )
        val mode = result.snapshot.mode as ActivePlaybackMode.Standard

        assertEquals(ExactTempo.of(120), mode.configuration.bpm)
        assertEquals(80_000L, result.snapshot.origin?.originFrame)
    }

    @Test
    fun startStopAndIdentityBoundariesAreExplicit() {
        val idle = PlaybackSnapshot(soundConfiguration())
        val started = applied(
            CommandBoundary.apply(
                idle,
                listOf(
                    StartStandard(
                        metadata(0, sessionID = 7),
                        LogicalPlaybackID(99),
                        standardConfiguration(),
                        null
                    )
                ),
                SessionOrigin(SessionID(7), 10_000)
            )
        )

        assertEquals(LogicalPlaybackID(99), started.snapshot.logicalPlaybackID)
        assertEquals(SessionID(7), started.snapshot.origin?.sessionID)

        val stopped = applied(
            CommandBoundary.apply(
                started.snapshot,
                listOf(Stop(metadata(1, sessionID = 7))),
                SessionOrigin(SessionID(8), 20_000)
            )
        )

        assertNull(stopped.snapshot.logicalPlaybackID)
        assertNull(stopped.snapshot.origin)
        assertNull(stopped.snapshot.mode)
    }

    @Test
    fun staleUnorderedAndDuplicateCommandsFailBeforeMutation() {
        val current = standardSnapshot()

        assertThrows(IllegalArgumentException::class.java) {
            CommandBoundary.apply(
                current,
                listOf(SetTempo(metadata(1, sessionID = 99), ExactTempo.of(100))),
                SessionOrigin(SessionID(2), 1)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommandBoundary.apply(
                current,
                listOf(
                    SetTempo(metadata(2), ExactTempo.of(100)),
                    SetTempo(metadata(1), ExactTempo.of(110))
                ),
                SessionOrigin(SessionID(2), 1)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommandBoundary.apply(
                current,
                listOf(
                    SetTempo(metadata(1), ExactTempo.of(100)),
                    SetTempo(metadata(1), ExactTempo.of(110))
                ),
                SessionOrigin(SessionID(2), 1)
            )
        }
        val afterFirst = applied(
            CommandBoundary.apply(
                current,
                listOf(SetMute(metadata(4), true)),
                SessionOrigin(SessionID(1), 0)
            )
        ).snapshot
        assertThrows(IllegalArgumentException::class.java) {
            CommandBoundary.apply(
                afterFirst,
                listOf(SetMute(metadata(3), false)),
                SessionOrigin(SessionID(1), 0)
            )
        }
    }

    private fun standardSnapshot() = PlaybackSnapshot(
        soundConfiguration = soundConfiguration(),
        logicalPlaybackID = LogicalPlaybackID(44),
        origin = SessionOrigin(SessionID(1), 0),
        mode = ActivePlaybackMode.Standard(standardConfiguration(), null)
    )

    private fun polyrhythmSnapshot() = PlaybackSnapshot(
        soundConfiguration = soundConfiguration(),
        logicalPlaybackID = LogicalPlaybackID(44),
        origin = SessionOrigin(SessionID(1), 0),
        mode = ActivePlaybackMode.Polyrhythm(
            PolyrhythmConfiguration(ExactTempo.of(120), beats = 3, against = 2)
        )
    )

    private fun standardConfiguration() = StandardMetronomeConfiguration(
        bpm = ExactTempo.of(120),
        timing = StandardTiming.Regular(StandardSubdivision.QUARTER)
    )

    private fun soundConfiguration() = SoundConfiguration(
        beatSound = SoundID("CLICK_HI"),
        rhythmSound = SoundID("CLICK_LO"),
        soundBank = SoundBank.ACOUSTIC
    )

    private fun metadata(sequence: Long, sessionID: Long = 1) = CommandMetadata(
        sessionID = SessionID(sessionID),
        commandSequence = CommandSequence(sequence),
        submissionTimestampNanos = sequence * 1_000
    )

    private fun applied(result: BoundaryResult): BoundaryResult.Applied =
        result as BoundaryResult.Applied
}
