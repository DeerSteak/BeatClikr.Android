package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSystemStateTest {
    @Test
    fun activePlaybackProjectsPlayingWithTerminalActionsOnly() {
        val projected = playing().toSystemPlaybackProjection()

        assertEquals(SystemPlaybackStatus.PLAYING, projected.status)
        assertTrue(projected.canPause)
        assertTrue(projected.canStop)
        assertFalse(projected.canPlay)
        assertFalse(projected.canSeek)
        assertFalse(projected.canSkip)
        assertFalse(projected.canChangeSpeed)
    }

    @Test
    fun preparationAndStoppingProjectTheirActualTransitions() {
        assertEquals(
            SystemPlaybackStatus.CONNECTING,
            PlaybackTransportState.Preparing(context()).toSystemPlaybackProjection().status
        )
        assertEquals(
            SystemPlaybackStatus.STOPPING,
            PlaybackTransportState.Stopping(context()).toSystemPlaybackProjection().status
        )
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
