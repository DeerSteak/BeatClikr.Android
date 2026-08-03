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
        outputFramesPerBuffer = 192,
        bufferFrames = 384,
        performanceMode = AudioBackendPerformanceMode.LOW_LATENCY,
        bufferSizeInBytes = 768,
        renderChunkFrames = 192,
        estimatedOutputLatencyNanos = 10_000_000,
        queuedClicks = 10,
        queuedBeatClicks = 6,
        queuedRhythmClicks = 4,
        renderedChunks = 20,
        intendedFrames = 4_000,
        renderedFrames = 4_000,
        writtenFrames = 4_000,
        estimatedPresentedFrames = 3_800,
        mixDurationP50UpperBoundNanos = 1,
        mixDurationP95UpperBoundNanos = 2,
        mixDurationP99UpperBoundNanos = 3,
        maximumMixDurationNanos = 4,
        writeDurationP50UpperBoundNanos = 1,
        writeDurationP95UpperBoundNanos = 2,
        writeDurationP99UpperBoundNanos = 3,
        maximumWriteDurationNanos = 4,
        routeChangeCount = 1,
        deadlineMisses = 2,
        droppedEvents = 3,
        maxActiveClicks = 4,
        underrunCount = 5,
        underrunSkippedFrames = 6,
        frameCorrelation = AudioFrameCorrelation(4_000, 3_800, 1_000_000),
        latestBackendFailure = AudioBackendFailure(
            AudioBackendOperation.RENDER,
            AudioBackendFailureCode.WRITE_FAILED
        )
    )
}
