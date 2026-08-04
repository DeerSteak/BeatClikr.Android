package com.bfunkstudios.beatclikr.services

internal object PlaybackServiceDiagnostics {
    fun format(state: PlaybackTransportState, metrics: FrameAudioMetricsSnapshot?): String {
        val session = (state as? PlaybackTransportState.SessionState)?.context?.sessionId?.value
        return buildString {
            append("transport=").append(state.diagnosticName)
            append(" session=").append(session ?: "none")
            if (metrics == null) {
                append(" metrics=unavailable")
                return@buildString
            }
            append(" backend=").append(metrics.backend)
            append(" route=").append(metrics.route)
            append(" sampleRate=").append(metrics.sampleRate)
            append(" queuedClicks=").append(metrics.queuedClicks)
            append(" intendedFrames=").append(metrics.intendedFrames)
            append(" renderedFrames=").append(metrics.renderedFrames)
            append(" writtenFrames=").append(metrics.writtenFrames)
            append(" deadlineMisses=").append(metrics.deadlineMisses)
            append(" droppedEvents=").append(metrics.droppedEvents)
            append(" underruns=").append(metrics.underrunCount)
            append(" underrunSkippedFrames=").append(metrics.underrunSkippedFrames)
            append(" routeChanges=").append(metrics.routeChangeCount)
            append(" backendFailure=").append(metrics.latestBackendFailure ?: "none")
        }
    }
}
