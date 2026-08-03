package com.bfunkstudios.beatclikr.music

import com.bfunkstudios.beatclikr.data.SoundBank as AppSoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.ActiveSoundConfiguration
import com.bfunkstudios.beatclikr.services.AudioBackendType
import com.bfunkstudios.beatclikr.services.AudioOutputRoute
import com.bfunkstudios.beatclikr.services.CommittedPlaybackConfiguration
import com.bfunkstudios.beatclikr.services.FrameAudioMetricsSnapshot
import com.bfunkstudios.beatclikr.services.FrameAudioRenderedEventBatch
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import com.bfunkstudios.beatclikr.services.PlaybackCoordinator
import com.bfunkstudios.beatclikr.services.PlaybackEnginePort
import com.bfunkstudios.beatclikr.services.PlaybackEngineStartEvidence
import com.bfunkstudios.beatclikr.services.PlaybackEngineTransportObserver
import com.bfunkstudios.beatclikr.services.PlaybackEngineUpdateResult
import com.bfunkstudios.beatclikr.services.PlaybackIntent
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.PlaybackTransportState
import com.bfunkstudios.beatclikr.services.PolyrhythmAudioEngineDelegate
import com.bfunkstudios.beatclikr.services.SoundPreparationFailure
import com.bfunkstudios.beatclikr.services.SoundPreparationPublication
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
    fun tb009_mt019_mt020_mt028_productionStandardUpdatesPreserveSessionAndPublishCompleteState() {
        val engine = QualificationEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val session = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId

            coordinator.submit(
                PlaybackIntent.UpdateStandard(
                    137.5f,
                    2,
                    listOf(true, false, false, true, false),
                    true
                )
            )
            assertTrue(coordinator.awaitControlIdle())

            val playing = coordinator.transportState.value as PlaybackTransportState.Playing
            val configuration = playing.context.configuration as CommittedPlaybackConfiguration.Standard
            assertEquals(session, playing.context.sessionId)
            assertEquals(137.5f, configuration.bpm)
            assertEquals(2, configuration.subdivisions)
            assertEquals(listOf(true, false, false, true, false), configuration.accentPattern)
            assertTrue(configuration.alternateSixteenth)
            assertEquals(configuration, engine.standardUpdates.single())
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun tb010_mt023_productionReducerSerializesAndCoalescesCompleteConfigurations() {
        val engine = QualificationEngine(asynchronousUpdates = true)
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            coordinator.submit(PlaybackIntent.UpdateStandard(130f, 4, null, false))
            coordinator.submit(PlaybackIntent.UpdateStandard(140f, 2, null, false))
            coordinator.submit(PlaybackIntent.UpdateStandard(150f, 3, null, true))
            assertTrue(coordinator.awaitControlIdle())

            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())
            engine.completeNextUpdate()
            assertTrue(coordinator.awaitControlIdle())

            assertEquals(listOf(130f, 150f), engine.standardUpdates.map { it.bpm })
            val configuration = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.configuration as CommittedPlaybackConfiguration.Standard
            assertEquals(150f, configuration.bpm)
            assertEquals(3, configuration.subdivisions)
            assertTrue(configuration.alternateSixteenth)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun mt014_mt021_productionStopAndModeRestartUseDistinctTransportSessions() {
        val engine = QualificationEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            assertTrue(coordinator.awaitControlIdle())
            val standard = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.sessionId

            coordinator.submit(PlaybackIntent.Stop)
            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            assertTrue(coordinator.awaitControlIdle())
            val polyrhythm = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context

            assertNotEquals(standard, polyrhythm.sessionId)
            assertEquals(PlaybackMode.POLYRHYTHM, polyrhythm.mode)
        } finally {
            coordinator.release()
        }
    }

    @Test
    fun tb010_mt023_seededProductionIntentSequencesRetainTheFinalCompleteConfiguration() {
        val random = Random(0x41_00)
        val engine = QualificationEngine()
        val coordinator = PlaybackCoordinator(engine)
        try {
            coordinator.submit(PlaybackIntent.StartStandard(120f, 4, null, false))
            var expectedBpm = 120f
            var expectedSubdivisions = 4
            var expectedAlternate = false
            var expectedMuted = false
            repeat(1_000) {
                if (random.nextBoolean()) {
                    expectedBpm = random.nextInt(30, 241).toFloat()
                    expectedSubdivisions = random.nextInt(1, 5)
                    expectedAlternate = random.nextBoolean()
                    coordinator.submit(
                        PlaybackIntent.UpdateStandard(
                            expectedBpm,
                            expectedSubdivisions,
                            null,
                            expectedAlternate
                        )
                    )
                } else {
                    expectedMuted = random.nextBoolean()
                    coordinator.submit(PlaybackIntent.SetMuted(expectedMuted))
                }
            }
            assertTrue(coordinator.awaitControlIdle())

            val standard = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.configuration as CommittedPlaybackConfiguration.Standard
            assertEquals(expectedBpm, standard.bpm)
            assertEquals(expectedSubdivisions, standard.subdivisions)
            assertEquals(expectedAlternate, standard.alternateSixteenth)
            assertEquals(expectedMuted, standard.muted)

            coordinator.submit(PlaybackIntent.Stop)
            coordinator.submit(PlaybackIntent.StartPolyrhythm(120f, 3, 2))
            var expectedBeats = 3
            var expectedAgainst = 2
            repeat(1_000) {
                expectedBpm = random.nextInt(30, 241).toFloat()
                expectedBeats = random.nextInt(1, 16)
                expectedAgainst = random.nextInt(1, 16)
                coordinator.submit(
                    PlaybackIntent.UpdatePolyrhythm(
                        expectedBpm,
                        expectedBeats,
                        expectedAgainst
                    )
                )
            }
            assertTrue(coordinator.awaitControlIdle())

            val polyrhythm = (coordinator.transportState.value as PlaybackTransportState.Playing)
                .context.configuration as CommittedPlaybackConfiguration.Polyrhythm
            assertEquals(expectedBpm, polyrhythm.bpm)
            assertEquals(expectedBeats, polyrhythm.beats)
            assertEquals(expectedAgainst, polyrhythm.against)
            assertEquals(polyrhythm, engine.polyrhythmUpdates.last())
        } finally {
            coordinator.release()
        }
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

    private fun standardTimeline(subdivision: StandardSubdivision) =
        StandardMetronomeTimeline(
            StandardMetronomeConfiguration(
                ExactTempo.of(120),
                StandardTiming.Regular(subdivision)
            ),
            48_000,
            SessionOrigin(SessionID(1), 0)
        )

    private class QualificationEngine(
        private val asynchronousUpdates: Boolean = false
    ) : PlaybackEnginePort {
        override var soundPreparationObserver: ((SoundPreparationPublication) -> Unit)? = null
        override var transportObserver: PlaybackEngineTransportObserver? = null
        override var delegate: MetronomeAudioEngineDelegate? = null
        override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate? = null
        override var isMuted = false
        val standardUpdates = mutableListOf<CommittedPlaybackConfiguration.Standard>()
        val polyrhythmUpdates = mutableListOf<CommittedPlaybackConfiguration.Polyrhythm>()
        private val updateCompletions = ArrayDeque<
            Pair<PlaybackSessionId, (PlaybackEngineUpdateResult) -> Unit>
        >()
        private val sounds = ActiveSoundConfiguration(
            AppSoundBank.ACOUSTIC,
            SoundFile.CLICK_HI,
            SoundFile.CLICK_LO
        )

        override fun activeSoundConfiguration() = sounds
        override fun soundPreparationFailure(): SoundPreparationFailure? = null
        override fun prewarmAudioTrack() = Unit
        override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? = null
        override fun release() = Unit

        override fun beginStandardSession(
            sessionId: PlaybackSessionId,
            bpm: Float,
            subdivisions: Int,
            accentPattern: List<Boolean>?,
            alternateSixteenth: Boolean
        ) = publishStarted(sessionId)

        override fun beginPolyrhythmSession(
            sessionId: PlaybackSessionId,
            bpm: Float,
            beats: Int,
            against: Int
        ) = publishStarted(sessionId)

        override fun updateStandardSession(
            sessionId: PlaybackSessionId,
            configuration: CommittedPlaybackConfiguration.Standard,
            completion: (PlaybackEngineUpdateResult) -> Unit
        ) {
            standardUpdates += configuration
            completeOrQueue(sessionId, completion)
        }

        override fun updatePolyrhythmSession(
            sessionId: PlaybackSessionId,
            configuration: CommittedPlaybackConfiguration.Polyrhythm,
            completion: (PlaybackEngineUpdateResult) -> Unit
        ) {
            polyrhythmUpdates += configuration
            completeOrQueue(sessionId, completion)
        }

        override fun stopSession(sessionId: PlaybackSessionId, mode: PlaybackMode) {
            transportObserver?.engineStopped(sessionId)
        }

        override fun drainRenderedEvents(afterCaptureSequence: Long): FrameAudioRenderedEventBatch? =
            null

        override fun selectSounds(
            requestSequence: Long,
            beatResourceId: Int,
            rhythmResourceId: Int
        ) = Unit

        override fun selectSoundBank(requestSequence: Long, bank: AppSoundBank) = Unit
        override fun prepareSounds(requestSequence: Long, sounds: Collection<SoundFile>) = Unit

        fun completeNextUpdate() {
            val (sessionId, completion) = updateCompletions.removeFirst()
            completion(PlaybackEngineUpdateResult.Accepted(sessionId))
        }

        private fun completeOrQueue(
            sessionId: PlaybackSessionId,
            completion: (PlaybackEngineUpdateResult) -> Unit
        ) {
            if (asynchronousUpdates) {
                updateCompletions.addLast(sessionId to completion)
            } else {
                completion(PlaybackEngineUpdateResult.Accepted(sessionId))
            }
        }

        private fun publishStarted(sessionId: PlaybackSessionId) {
            transportObserver?.engineStarted(
                PlaybackEngineStartEvidence(
                    sessionId,
                    sounds,
                    AudioOutputRoute.BUILT_IN,
                    AudioBackendType.AUDIO_TRACK,
                    0
                )
            )
        }
    }

    private companion object {
        val backendSampleRates = listOf(44_100, 48_000)
        const val TWELVE_HOURS_SECONDS = 12L * 60 * 60
    }
}
