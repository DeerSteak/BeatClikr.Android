package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackForegroundServiceControllerTest {
    private val transport = MutableStateFlow<PlaybackTransportState>(PlaybackTransportState.Idle)
    private val playback = mockk<PlaybackObservation> {
        every { transportState } returns transport
        every { committedEvents } returns MutableSharedFlow()
    }
    private val gateway = RecordingGateway()
    private val controller = PlaybackForegroundServiceController(
        playback,
        gateway,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    )

    @Test
    fun sessionLifetimeStartsServiceOnceAcrossIntermediateStates() {
        controller.start()

        transport.value = PlaybackTransportState.Preparing(context())
        transport.value = PlaybackTransportState.Stopping(context())

        assertEquals(1, gateway.starts)
        assertEquals(1, gateway.stops)
    }

    @Test
    fun idleAfterSessionStopsService() {
        controller.start()
        transport.value = PlaybackTransportState.Preparing(context())

        transport.value = PlaybackTransportState.Idle

        assertEquals(1, gateway.starts)
        assertEquals(2, gateway.stops)
    }

    @Test
    fun repeatedStartDoesNotInstallAnotherCollector() {
        controller.start()
        controller.start()

        transport.value = PlaybackTransportState.Preparing(context())

        assertEquals(1, gateway.starts)
    }

    private fun context() = PlaybackSessionContext(
        PlaybackSessionId(9),
        PlaybackMode.STANDARD,
        CommittedPlaybackConfiguration.Standard(120f, 1, null, false, false),
        startOrigin = PlaybackStartOrigin.USER,
        practiceItem = PracticeItemSnapshot.metronome()
    )

    private class RecordingGateway : PlaybackForegroundServiceGateway {
        var starts = 0
        var stops = 0

        override fun start() {
            starts += 1
        }

        override fun stop() {
            stops += 1
        }
    }
}
