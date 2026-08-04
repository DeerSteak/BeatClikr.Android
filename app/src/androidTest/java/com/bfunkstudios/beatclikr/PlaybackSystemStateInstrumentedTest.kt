package com.bfunkstudios.beatclikr

import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.ActiveSoundConfiguration
import com.bfunkstudios.beatclikr.services.AudioBackendType
import com.bfunkstudios.beatclikr.services.AudioOutputRoute
import com.bfunkstudios.beatclikr.services.CommittedPlaybackConfiguration
import com.bfunkstudios.beatclikr.services.PlaybackFailureReason
import com.bfunkstudios.beatclikr.services.PlaybackInterruptionReason
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackSessionContext
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.PlaybackStartOrigin
import com.bfunkstudios.beatclikr.services.PlaybackTransportState
import com.bfunkstudios.beatclikr.services.toSystemPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackSystemStateInstrumentedTest {
    @Test
    fun activePlaybackBuildsPlayingStateWithTerminalActionsOnly() {
        val state = playing().toSystemPlaybackState()

        assertEquals(PlaybackState.STATE_PLAYING, state.state)
        assertEquals(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP, state.actions)
    }

    @Test
    fun terminalAndTransitionalStatesBuildExpectedSystemStates() {
        assertSystemState(PlaybackState.STATE_CONNECTING, PlaybackTransportState.Preparing(context()))
        assertSystemState(PlaybackState.STATE_STOPPED, PlaybackTransportState.Stopping(context()))
        assertSystemState(
            PlaybackState.STATE_STOPPED,
            PlaybackTransportState.Interrupted(context(), PlaybackInterruptionReason.AudioFocusLost)
        )
        assertSystemState(
            PlaybackState.STATE_STOPPED,
            PlaybackTransportState.Failed(context(), PlaybackFailureReason.Engine("test failure"))
        )
    }

    private fun assertSystemState(expected: Int, transport: PlaybackTransportState) {
        assertEquals(expected, transport.toSystemPlaybackState().state)
    }

    private fun playing() = PlaybackTransportState.Playing(
        context().copy(
            audibleSounds = ActiveSoundConfiguration(
                SoundBank.ACOUSTIC,
                SoundFile.CLICK_HI,
                SoundFile.CLICK_LO
            ),
            route = AudioOutputRoute.BUILT_IN,
            backend = AudioBackendType.AUDIO_TRACK
        )
    )

    private fun context() = PlaybackSessionContext(
        PlaybackSessionId(3),
        PlaybackMode.STANDARD,
        CommittedPlaybackConfiguration.Standard(120f, 1, null, false, false),
        startOrigin = PlaybackStartOrigin.USER,
        practiceItem = PracticeItemSnapshot.metronome()
    )
}
