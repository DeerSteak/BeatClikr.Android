package com.bfunkstudios.beatclikr.services

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class PlaybackServiceCommandHandlerTest {
    private val playback = mockk<IAudioPlayerService>(relaxed = true)
    private val handler = PlaybackServiceCommandHandler(playback)

    @Test
    fun stopActionEndsPlayback() {
        handler.handle(PlaybackForegroundService.ACTION_STOP)

        verify(exactly = 1) { playback.submit(PlaybackIntent.Stop) }
    }

    @Test
    fun mediaPauseAndStopShareTheSameTerminalCommand() {
        handler.stop()
        handler.stop()

        verify(exactly = 2) { playback.submit(PlaybackIntent.Stop) }
    }

    @Test
    fun missingOrUnknownActionNeverStartsOrStopsPlayback() {
        handler.handle(null)
        handler.handle("unexpected")

        verify(exactly = 0) { playback.submit(any()) }
    }
}
