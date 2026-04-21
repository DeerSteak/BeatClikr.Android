package com.bfunkstudios.beatclikr.di

import android.content.Context
import com.bfunkstudios.beatclikr.data.AppPreferences
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.services.AudioPlayerService
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAudioPlayerService(@ApplicationContext context: Context): IAudioPlayerService =
        AudioPlayerService.getInstance(context)

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): IAppPreferences =
        AppPreferences(context)
}
