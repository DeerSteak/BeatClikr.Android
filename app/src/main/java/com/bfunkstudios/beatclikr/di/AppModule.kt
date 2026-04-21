package com.bfunkstudios.beatclikr.di

import android.content.Context
import androidx.room.Room
import com.bfunkstudios.beatclikr.data.AppPreferences
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SongRepository
import com.bfunkstudios.beatclikr.data.SongRepositoryImpl
import com.bfunkstudios.beatclikr.data.db.BeatClikrDatabase
import com.bfunkstudios.beatclikr.services.AudioPlayerService
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
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

    companion object {

        @Provides @Singleton
        fun provideAudioPlayerService(@ApplicationContext context: Context): IAudioPlayerService =
            AudioPlayerService.getInstance(context)

        @Provides @Singleton
        fun provideAppPreferences(@ApplicationContext context: Context): IAppPreferences =
            AppPreferences(context)

        @Provides @Singleton
        fun provideDatabase(@ApplicationContext context: Context): BeatClikrDatabase =
            Room.databaseBuilder(context, BeatClikrDatabase::class.java, "beatclikr.db").build()

        @Provides @Singleton
        fun provideSongDao(db: BeatClikrDatabase) = db.songDao()

        @Provides @Singleton @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
