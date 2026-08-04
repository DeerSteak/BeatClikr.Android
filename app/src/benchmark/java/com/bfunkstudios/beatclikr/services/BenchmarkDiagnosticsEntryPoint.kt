package com.bfunkstudios.beatclikr.services

import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

class BenchmarkPlaybackDiagnostics(
    private val audio: IAudioPlayerService
) {
    private val coordinator: PlaybackCoordinator
        get() = audio as PlaybackCoordinator

    fun isPlaying(): Boolean = coordinator.transportState.value is PlaybackTransportState.Playing

    fun sessionId(): Long = playing().context.sessionId.value

    fun bpm(): Float = configuration().bpm

    fun subdivisions(): Int = configuration().subdivisions

    fun renderedChunks(): Long = metrics().renderedChunks

    fun intendedFrames(): Long = metrics().intendedFrames

    fun renderedFrames(): Long = metrics().renderedFrames

    fun writtenFrames(): Long = metrics().writtenFrames

    fun deadlineMisses(): Long = metrics().deadlineMisses

    fun droppedEvents(): Long = metrics().droppedEvents

    fun underrunCount(): Int = metrics().underrunCount

    fun underrunSkippedFrames(): Long = metrics().underrunSkippedFrames

    fun metricsLog(): String = metrics().let {
        "backend=${it.backend} route=${it.route} sampleRate=${it.sampleRate} " +
            "burstFrames=${it.outputFramesPerBuffer} bufferFrames=${it.bufferFrames}"
    }

    private fun playing(): PlaybackTransportState.Playing =
        coordinator.transportState.value as PlaybackTransportState.Playing

    private fun configuration(): CommittedPlaybackConfiguration.Standard =
        playing().context.configuration as CommittedPlaybackConfiguration.Standard

    private fun metrics(): FrameAudioMetricsSnapshot =
        requireNotNull(coordinator.getFrameAudioMetricsSnapshot())
}

@Module
@InstallIn(SingletonComponent::class)
object BenchmarkDiagnosticsModule {
    @Provides
    @Singleton
    fun provideBenchmarkDiagnostics(audio: IAudioPlayerService) =
        BenchmarkPlaybackDiagnostics(audio)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BenchmarkDiagnosticsEntryPoint {
    fun diagnostics(): BenchmarkPlaybackDiagnostics
}
