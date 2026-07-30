package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTransportStateTest {
    private val first = playingContext(1)
    private val second = playingContext(2)

    @Test
    fun standardLifecycleTransitionsAreLegal() {
        val states = listOf(
            PlaybackTransportState.Idle,
            PlaybackTransportState.Preparing(first, PlaybackPrerequisites.READY),
            PlaybackTransportState.Starting(first),
            PlaybackTransportState.Playing(first),
            PlaybackTransportState.Stopping(first),
            PlaybackTransportState.Idle
        )

        states.zipWithNext().forEach { (from, to) ->
            assertSame(to, PlaybackTransportTransitions.requireLegal(from, to))
        }
    }

    @Test
    fun replacementSkipsObservableIdle() {
        val stopping = PlaybackTransportState.Stopping(first)
        val preparing = PlaybackTransportState.Preparing(
            second,
            PlaybackPrerequisites.READY
        )

        assertTrue(PlaybackTransportTransitions.isLegal(stopping, preparing))
        assertFalse(
            PlaybackTransportTransitions.isLegal(
                PlaybackTransportState.Playing(first),
                preparing
            )
        )
    }

    @Test
    fun interruptionAndFailureRequireIdleBeforeRestart() {
        val interrupted = PlaybackTransportState.Interrupted(
            first,
            PlaybackInterruptionReason.RouteLost
        )
        val failed = PlaybackTransportState.Failed(
            first,
            PlaybackFailureReason.Engine("write failed")
        )
        val preparing = PlaybackTransportState.Preparing(
            second,
            PlaybackPrerequisites.READY
        )

        assertTrue(
            PlaybackTransportTransitions.isLegal(
                interrupted,
                PlaybackTransportState.Idle
            )
        )
        assertTrue(
            PlaybackTransportTransitions.isLegal(
                failed,
                PlaybackTransportState.Idle
            )
        )
        assertFalse(PlaybackTransportTransitions.isLegal(interrupted, preparing))
        assertFalse(PlaybackTransportTransitions.isLegal(failed, preparing))
    }

    @Test
    fun staleSessionCannotAdvanceCurrentLifecycle() {
        val current = PlaybackTransportState.Starting(second)

        assertFalse(
            PlaybackTransportTransitions.isLegal(
                current,
                PlaybackTransportState.Playing(first)
            )
        )
        assertFalse(
            PlaybackTransportTransitions.isLegal(
                current,
                PlaybackTransportState.Failed(
                    first,
                    PlaybackFailureReason.Engine("stale")
                )
            )
        )
    }

    @Test
    fun preparingCanFailWhenPrerequisitesAreUnavailable() {
        val prerequisites = PlaybackPrerequisites(
            audioFocusReady = false,
            routeReady = true
        )
        val preparing = PlaybackTransportState.Preparing(first, prerequisites)
        val failed = PlaybackTransportState.Failed(
            first,
            PlaybackFailureReason.PrerequisiteUnavailable(prerequisites.missing)
        )

        assertTrue(PlaybackTransportTransitions.isLegal(preparing, failed))
        assertTrue(
            (failed.reason as PlaybackFailureReason.PrerequisiteUnavailable)
                .missing
                .contains(PlaybackPrerequisite.AUDIO_FOCUS)
        )
    }

    @Test
    fun inPlacePlayingAmendmentCommitsConfigurationForSameSession() {
        val playing = PlaybackTransportState.Playing(first)
        val amended = PlaybackTransportState.Playing(
            first.copy(
                configuration = (first.configuration as
                    CommittedPlaybackConfiguration.Standard).copy(
                    bpm = 121f,
                    muted = true
                )
            )
        )

        assertTrue(PlaybackTransportTransitions.isLegal(playing, amended))
        assertFalse(
            PlaybackTransportTransitions.isLegal(
                playing,
                PlaybackTransportState.Playing(
                    amended.context.copy(sessionId = PlaybackSessionId(2))
                )
            )
        )
    }

    @Test
    fun preparingDoesNotRequireResultsProducedByPreparationOrBackendOpen() {
        val context = requestedContext(1)
        val preparing = PlaybackTransportState.Preparing(
            context,
            PlaybackPrerequisites.READY
        )

        assertTrue(preparing.context.audibleSounds == null)
        assertTrue(preparing.context.route == null)
        assertTrue(preparing.context.backend == null)
    }

    @Test
    fun startingCanAmendBackendFactsBeforePlaying() {
        val startingContext = requestedContext(1).copy(
            audibleSounds = sounds()
        )
        val starting = PlaybackTransportState.Starting(startingContext)
        val opened = PlaybackTransportState.Starting(
            startingContext.copy(
                route = AudioOutputRoute.UNKNOWN,
                backend = AudioBackendType.AUDIO_TRACK
            )
        )

        assertTrue(PlaybackTransportTransitions.isLegal(starting, opened))
        assertTrue(
            PlaybackTransportTransitions.isLegal(
                opened,
                PlaybackTransportState.Playing(opened.context)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun startingRejectsMissingPreparedSounds() {
        PlaybackTransportState.Starting(requestedContext(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun playingRejectsMissingOpenedBackendFacts() {
        PlaybackTransportState.Playing(
            requestedContext(1).copy(audibleSounds = sounds())
        )
    }

    @Test(expected = IllegalPlaybackTransition::class)
    fun illegalTransitionThrows() {
        PlaybackTransportTransitions.requireLegal(
            PlaybackTransportState.Idle,
            PlaybackTransportState.Playing(first)
        )
    }

    @Test
    fun transitionMatrixCoversEveryLifecycleEdge() {
        val idle = PlaybackTransportState.Idle
        val preparing = PlaybackTransportState.Preparing(
            first,
            PlaybackPrerequisites.READY
        )
        val starting = PlaybackTransportState.Starting(first)
        val playing = PlaybackTransportState.Playing(first)
        val stopping = PlaybackTransportState.Stopping(first)
        val interrupted = PlaybackTransportState.Interrupted(
            first,
            PlaybackInterruptionReason.RouteLost
        )
        val failed = PlaybackTransportState.Failed(
            first,
            PlaybackFailureReason.Engine("failed")
        )
        val replacement = PlaybackTransportState.Preparing(
            second,
            PlaybackPrerequisites.READY
        )
        val states = listOf(
            idle,
            preparing,
            starting,
            playing,
            stopping,
            interrupted,
            failed,
            replacement
        )
        val legal = setOf(
            idle to idle,
            idle to preparing,
            idle to replacement,
            preparing to preparing,
            preparing to starting,
            preparing to stopping,
            preparing to failed,
            starting to starting,
            starting to playing,
            starting to stopping,
            starting to failed,
            playing to playing,
            playing to stopping,
            playing to interrupted,
            playing to failed,
            stopping to stopping,
            stopping to idle,
            stopping to replacement,
            interrupted to idle,
            failed to idle,
            replacement to replacement
        )

        states.forEach { from ->
            states.forEach { to ->
                assertEquals(
                    "${from::class.simpleName} -> ${to::class.simpleName}",
                    from to to in legal,
                    PlaybackTransportTransitions.isLegal(from, to)
                )
            }
        }
    }

    private fun requestedContext(id: Long) = PlaybackSessionContext(
        sessionId = PlaybackSessionId(id),
        mode = PlaybackMode.STANDARD,
        configuration = CommittedPlaybackConfiguration.Standard(
            bpm = 120f,
            subdivisions = 4,
            accentPattern = null,
            alternateSixteenth = false,
            muted = false
        ),
        startOrigin = PlaybackStartOrigin.USER
    )

    private fun playingContext(id: Long) = requestedContext(id).copy(
        audibleSounds = sounds(),
        route = AudioOutputRoute.UNKNOWN,
        backend = AudioBackendType.AUDIO_TRACK
    )

    private fun sounds() = ActiveSoundConfiguration(
            SoundBank.ACOUSTIC,
            SoundFile.CLICK_HI,
            SoundFile.CLICK_LO
    )
}
