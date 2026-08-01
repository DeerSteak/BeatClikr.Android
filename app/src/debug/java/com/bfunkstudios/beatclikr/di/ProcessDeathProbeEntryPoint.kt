package com.bfunkstudios.beatclikr.di

import com.bfunkstudios.beatclikr.services.PlaybackCoordinator
import com.bfunkstudios.beatclikr.services.PlaybackEnginePort
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProcessDeathProbeEntryPoint {
    fun coordinator(): PlaybackCoordinator
    fun engine(): PlaybackEnginePort
}
