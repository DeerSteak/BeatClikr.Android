package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceDiagnosticsTest {
    @Test
    fun activeSnapshotReportsQualificationCounters() {
        val output = PlaybackServiceDiagnostics.format(
            PlaybackTransportState.Preparing(context()),
            FrameAudioMetricsSnapshot(
                backend = AudioBackendType.AUDIO_TRACK,
                route = AudioOutputRoute.BUILT_IN,
                sampleRate = 48_000,
                queuedClicks = 12,
                intendedFrames = 100,
                renderedFrames = 100,
                writtenFrames = 100,
                deadlineMisses = 0,
                droppedEvents = 0,
                underrunCount = 1,
                underrunSkippedFrames = 0,
                routeChangeCount = 0
            )
        )

        assertTrue(output.contains("transport=Preparing session=4"))
        assertTrue(output.contains("queuedClicks=12"))
        assertTrue(output.contains("deadlineMisses=0 droppedEvents=0 underruns=1"))
        assertTrue(output.endsWith("backendFailure=none"))
    }

    @Test
    fun idleWithoutEngineSnapshotIsExplicit() {
        assertEquals(
            "transport=Idle session=none metrics=unavailable",
            PlaybackServiceDiagnostics.format(PlaybackTransportState.Idle, null)
        )
    }

    private fun context() = PlaybackSessionContext(
        PlaybackSessionId(4),
        PlaybackMode.STANDARD,
        CommittedPlaybackConfiguration.Standard(120f, 1, null, false, false),
        startOrigin = PlaybackStartOrigin.USER,
        practiceItem = PracticeItemSnapshot.metronome()
    )
}
