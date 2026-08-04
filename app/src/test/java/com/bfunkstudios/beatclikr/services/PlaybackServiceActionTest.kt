package com.bfunkstudios.beatclikr.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackServiceActionTest {
    @Test
    fun onlyStopActionMapsToTerminalIntent() {
        assertEquals(
            PlaybackIntent.Stop,
            playbackIntentForServiceAction(PlaybackForegroundService.ACTION_STOP)
        )
        assertNull(playbackIntentForServiceAction(null))
        assertNull(playbackIntentForServiceAction("unexpected"))
    }
}
