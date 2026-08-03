package com.bfunkstudios.beatclikr.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDiagnosticsTest {
    @Test
    fun reportIsBoundedAndRedactsUnstructuredMetadata() {
        val transitions = (1L..30L).map {
            PlaybackLifecycleDiagnostic(it, "Playing/song=Secret", "Idle/path=/private/user")
        }
        val report = LocalDiagnostics.render(
            LocalDiagnosticSnapshot(
                "1.2.3\nsecret",
                42,
                "Device\n/private/user",
                "Android\t17",
                metrics(),
                transitions
            )
        )

        assertTrue(report.contains("route=BLUETOOTH"))
        assertTrue(report.contains("backend=AUDIO_TRACK"))
        assertTrue(report.contains("latency_confidence=timestamp_correlated"))
        assertTrue(report.contains("backend_failure=WRITE_FAILED"))
        assertFalse(report.contains("Secret"))
        assertFalse(report.contains("/private/user"))
        assertEquals(20, report.lineSequence().count { it.matches(Regex("\\d+:.*>.*")) })
    }

    private fun metrics() = FrameAudioMetricsSnapshot(
        backend = AudioBackendType.AUDIO_TRACK,
        route = AudioOutputRoute.BLUETOOTH,
        sampleRate = 48_000,
        channelCount = 1,
        bufferFrames = 384,
        estimatedOutputLatencyNanos = 10_000_000,
        deadlineMisses = 2,
        droppedEvents = 3,
        underrunCount = 5,
        frameCorrelation = AudioFrameCorrelation(4_000, 3_800, 1_000_000),
        latestBackendFailure = AudioBackendFailure(
            AudioBackendOperation.RENDER,
            AudioBackendFailureCode.WRITE_FAILED
        )
    )
}
