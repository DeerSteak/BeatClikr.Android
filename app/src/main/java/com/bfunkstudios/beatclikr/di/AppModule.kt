package com.bfunkstudios.beatclikr.di

import android.content.Context
import androidx.room.Room
import com.bfunkstudios.beatclikr.data.AppPreferences
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.PlaylistRepository
import com.bfunkstudios.beatclikr.data.PlaylistRepositoryImpl
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepository
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepositoryImpl
import com.bfunkstudios.beatclikr.data.SongRepository
import com.bfunkstudios.beatclikr.data.SongRepositoryImpl
import com.bfunkstudios.beatclikr.data.db.BeatClikrDatabase
import com.bfunkstudios.beatclikr.data.db.BeatClikrMigrations
import com.bfunkstudios.beatclikr.services.FlashlightService
import com.bfunkstudios.beatclikr.services.HapticFeedbackService
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.PlaybackCoordinator
import com.bfunkstudios.beatclikr.services.PlaybackObservation
import com.bfunkstudios.beatclikr.services.PlaybackLifecycleObservation
import com.bfunkstudios.beatclikr.services.AndroidPlaybackForegroundServiceGateway
import com.bfunkstudios.beatclikr.services.PlaybackForegroundServiceGateway
import com.bfunkstudios.beatclikr.services.PlaybackEnginePort
import com.bfunkstudios.beatclikr.services.IFlashlightService
import com.bfunkstudios.beatclikr.services.IHapticFeedbackService
import com.bfunkstudios.beatclikr.services.IPracticeReminderScheduler
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import com.bfunkstudios.beatclikr.services.PracticeReminderScheduler
import com.bfunkstudios.beatclikr.services.SecondaryOutputCoordinator
import com.bfunkstudios.beatclikr.services.SecondaryOutputObservation
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds @Singleton
    abstract fun bindSongRepository(impl: SongRepositoryImpl): SongRepository

    @Binds @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds @Singleton
    abstract fun bindPracticeHistoryRepository(impl: PracticeHistoryRepositoryImpl): PracticeHistoryRepository

    @Binds @Singleton
    abstract fun bindPracticeReminderScheduler(impl: PracticeReminderScheduler): IPracticeReminderScheduler

    @Binds @Singleton
    abstract fun bindPlaybackForegroundServiceGateway(
        impl: AndroidPlaybackForegroundServiceGateway
    ): PlaybackForegroundServiceGateway

    companion object {

        @Provides @Singleton
        fun providePlaybackEngine(@ApplicationContext context: Context): PlaybackEnginePort =
            debugPlaybackEngine(context) ?: MetronomeAudioEngine(context.applicationContext)

        private fun debugPlaybackEngine(context: Context): PlaybackEnginePort? {
            if (!com.bfunkstudios.beatclikr.BuildConfig.DEBUG) return null
            return runCatching {
                Class.forName("com.bfunkstudios.beatclikr.ProcessDeathTestEngine")
                    .getMethod("create", Context::class.java)
                    .invoke(null, context) as? PlaybackEnginePort
            }.getOrNull()
        }

        @Provides @Singleton
        fun providePlaybackCoordinator(engine: PlaybackEnginePort): PlaybackCoordinator =
            PlaybackCoordinator(engine)

        @Provides @Singleton
        fun provideAudioPlayerService(coordinator: PlaybackCoordinator): IAudioPlayerService =
            coordinator

        @Provides @Singleton
        fun providePlaybackObservation(coordinator: PlaybackCoordinator): PlaybackObservation =
            coordinator

        @Provides
        fun providePlaybackLifecycleObservation(
            coordinator: PlaybackCoordinator
        ): PlaybackLifecycleObservation = coordinator

        @Provides @Singleton
        fun provideFlashlightService(@ApplicationContext context: Context): IFlashlightService =
            FlashlightService(context)

        @Provides @Singleton
        fun provideHapticFeedbackService(@ApplicationContext context: Context): IHapticFeedbackService =
            HapticFeedbackService(context)

        @Provides @Singleton
        fun provideSecondaryOutputCoordinator(
            playback: PlaybackObservation,
            prefs: IAppPreferences,
            flashlight: IFlashlightService,
            haptics: IHapticFeedbackService,
            @ApplicationScope scope: CoroutineScope
        ): SecondaryOutputCoordinator =
            SecondaryOutputCoordinator(playback, prefs, flashlight, haptics, scope)

        @Provides @Singleton
        fun provideSecondaryOutputObservation(
            coordinator: SecondaryOutputCoordinator
        ): SecondaryOutputObservation = coordinator

        @Provides @Singleton
        fun provideAppPreferences(@ApplicationContext context: Context): IAppPreferences =
            AppPreferences(context)

        @Provides @Singleton
        fun provideDatabase(@ApplicationContext context: Context): BeatClikrDatabase =
            Room.databaseBuilder(context, BeatClikrDatabase::class.java, "beatclikr.db")
                .addMigrations(BeatClikrMigrations.MIGRATION_4_5)
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
                .build()

        @Provides @Singleton
        fun provideSongDao(db: BeatClikrDatabase) = db.songDao()

        @Provides @Singleton
        fun providePlaylistDao(db: BeatClikrDatabase) = db.playlistDao()

        @Provides @Singleton
        fun providePracticeHistoryDao(db: BeatClikrDatabase) = db.practiceHistoryDao()

        @Provides @Singleton @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
