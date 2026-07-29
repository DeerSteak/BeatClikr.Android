package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TempoRampStateTest {

    @Test
    fun mt025_supportedChoicesMatchIos() {
        assertEquals(listOf(1, 2, 5, 10), TempoRampConfiguration.supportedIncrements)
        assertEquals(listOf(4, 8, 16, 32, 48, 64), TempoRampConfiguration.supportedIntervals)
    }

    @Test
    fun mt026_initialBeatEstablishesZeroAndEachIntervalRequestsRestart() {
        val configuration = TempoRampConfiguration(rampIncrement = 5, rampInterval = 4)
        var state = TempoRampState(startingBpm = ExactTempo.of(100))

        repeat(4) {
            val advance = state.advance(configuration, standardEvent(BeatIdentity.BEAT))
            assertNull(advance.restartBpm)
            state = advance.state
        }
        val step = state.advance(configuration, standardEvent(BeatIdentity.BEAT))

        assertEquals(ExactTempo.of(105), step.restartBpm)
        assertEquals(ExactTempo.of(105), step.state.currentBpm)
        assertEquals(4L, step.state.rampBeatCount)
    }

    @Test
    fun mt027_onlyStandardBeatsAndAccentsEnterRampState() {
        val configuration = TempoRampConfiguration(rampIncrement = 2, rampInterval = 4)
        var state = TempoRampState(startingBpm = ExactTempo.of(120))
        val ignored = listOf(
            standardEvent(BeatIdentity.SUBDIVISION),
            polyrhythmEvent()
        )

        ignored.forEach { state = state.advance(configuration, it).state }
        repeat(4) { state = state.advance(configuration, standardEvent(BeatIdentity.ACCENT)).state }
        val step = state.advance(configuration, standardEvent(BeatIdentity.ACCENT))

        assertEquals(ExactTempo.of(122), step.restartBpm)
        assertEquals(4L, step.state.rampBeatCount)
    }

    @Test
    fun mt026_mt028_stepsAreExactCappedAndDeterministic() {
        val configuration = TempoRampConfiguration(rampIncrement = 2, rampInterval = 4)
        var state = TempoRampState(startingBpm = ExactTempo.parse("237.5"))

        repeat(4) { state = state.advance(configuration, standardEvent(BeatIdentity.BEAT)).state }
        val first = state.advance(configuration, standardEvent(BeatIdentity.BEAT))
        assertEquals(ExactTempo.parse("239.5"), first.restartBpm)

        state = first.state
        repeat(3) { state = state.advance(configuration, standardEvent(BeatIdentity.BEAT)).state }
        val capped = state.advance(configuration, standardEvent(BeatIdentity.BEAT))
        assertEquals(ExactTempo.of(240), capped.restartBpm)

        state = capped.state
        repeat(3) { state = state.advance(configuration, standardEvent(BeatIdentity.BEAT)).state }
        val atMaximum = state.advance(configuration, standardEvent(BeatIdentity.BEAT))
        assertNull(atMaximum.restartBpm)
        assertEquals(ExactTempo.of(240), atMaximum.state.currentBpm)
    }

    @Test
    fun newRampStateRestoresStartingTempoAndInitialBeatRule() {
        val configuration = TempoRampConfiguration(rampIncrement = 10, rampInterval = 4)
        var state = TempoRampState(startingBpm = ExactTempo.of(90))
        repeat(5) { state = state.advance(configuration, standardEvent(BeatIdentity.BEAT)).state }

        val restarted = TempoRampState(startingBpm = state.startingBpm)
        val first = restarted.advance(configuration, standardEvent(BeatIdentity.BEAT))

        assertEquals(ExactTempo.of(90), restarted.currentBpm)
        assertEquals(-1L, restarted.rampBeatCount)
        assertNull(first.restartBpm)
        assertEquals(0L, first.state.rampBeatCount)
    }

    private fun standardEvent(identity: BeatIdentity) = FrameEvent(
        sequence = EventSequence(SessionID(1), 0),
        intendedFrame = 0,
        primary = EventVoice(
            role = MusicalEventRole.STANDARD,
            soundRole = SoundRole.BEAT,
            beatIdentity = identity,
            position = CyclePosition(0, 0)
        )
    )

    private fun polyrhythmEvent() = FrameEvent(
        sequence = EventSequence(SessionID(2), 0),
        intendedFrame = 0,
        primary = EventVoice(
            role = MusicalEventRole.POLYRHYTHM_BEAT,
            soundRole = SoundRole.BEAT,
            beatIdentity = BeatIdentity.BEAT,
            position = CyclePosition(0, 0)
        )
    )
}
