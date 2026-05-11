package com.bfunkstudios.beatclikr

import android.content.Context
import androidx.room.Room
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.PlaylistRepository
import com.bfunkstudios.beatclikr.data.PlaylistRepositoryImpl
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepository
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepositoryImpl
import com.bfunkstudios.beatclikr.data.SongRepository
import com.bfunkstudios.beatclikr.data.SongRepositoryImpl
import com.bfunkstudios.beatclikr.data.db.BeatClikrDatabase
import com.bfunkstudios.beatclikr.data.db.PlaylistDao
import com.bfunkstudios.beatclikr.data.db.PracticeHistoryDao
import com.bfunkstudios.beatclikr.data.db.SongDao
import com.bfunkstudios.beatclikr.di.AppModule
import com.bfunkstudios.beatclikr.di.ApplicationScope
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.IFlashlightService
import com.bfunkstudios.beatclikr.services.IHapticFeedbackService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidTest
@UninstallModules(AppModule::class)
class InstantMetronomeViewTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var audio: IAudioPlayerService

    @Module
    @InstallIn(SingletonComponent::class)
    object TestModule {
        @Provides @Singleton
        fun provideAudio(): IAudioPlayerService = FakeAudioPlayerService()

        @Provides @Singleton
        fun provideFlashlight(): IFlashlightService = FakeFlashlightService()

        @Provides @Singleton
        fun provideHaptics(): IHapticFeedbackService = FakeHapticFeedbackService()

        @Provides @Singleton
        fun providePrefs(): IAppPreferences = FakeAppPreferences()

        @Provides @Singleton
        fun provideDatabase(@ApplicationContext context: Context): BeatClikrDatabase =
            Room.inMemoryDatabaseBuilder(context, BeatClikrDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        @Provides @Singleton
        fun provideSongDao(db: BeatClikrDatabase): SongDao = db.songDao()

        @Provides @Singleton
        fun providePlaylistDao(db: BeatClikrDatabase): PlaylistDao = db.playlistDao()

        @Provides @Singleton
        fun providePracticeHistoryDao(db: BeatClikrDatabase): PracticeHistoryDao = db.practiceHistoryDao()

        @Provides @Singleton
        fun provideSongRepository(impl: SongRepositoryImpl): SongRepository = impl

        @Provides @Singleton
        fun providePlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository = impl

        @Provides @Singleton
        fun providePracticeHistoryRepository(impl: PracticeHistoryRepositoryImpl): PracticeHistoryRepository = impl

        @Provides @Singleton @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private val activity get() = composeRule.activity

    @Test
    fun bpmDisplaysOnLaunch() {
        composeRule.onNodeWithText("120").assertIsDisplayed()
    }

    @Test
    fun bpmLabelDisplaysOnLaunch() {
        composeRule.onNodeWithText(activity.getString(R.string.bpm)).assertIsDisplayed()
    }

    @Test
    fun playButtonDisplaysOnLaunch() {
        composeRule.onNodeWithText(activity.getString(R.string.play)).assertIsDisplayed()
    }

    @Test
    fun tappingPlayShowsPauseButton() {
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.pause)).assertIsDisplayed()
    }

    @Test
    fun tappingPauseShowsPlayButton() {
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.pause)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.play)).assertIsDisplayed()
    }

    @Test
    fun tappingPlayCallsStartOnAudioService() {
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        val fake = audio as FakeAudioPlayerService
        assert(fake.startCount == 1)
    }

    @Test
    fun tappingPauseCallsStopOnAudioService() {
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.pause)).performClick()
        val fake = audio as FakeAudioPlayerService
        assert(fake.stopCount == 1)
    }

    @Test
    fun quarterSubdivisionDisplaysOnLaunch() {
        composeRule.onNodeWithText(activity.getString(R.string.subdivision_quarter)).assertIsDisplayed()
    }

    @Test
    fun allSubdivisionButtonsDisplay() {
        composeRule.onNodeWithText(activity.getString(R.string.subdivision_quarter)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.subdivision_eighth)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.subdivision_triplet)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.subdivision_sixteenth)).assertIsDisplayed()
    }

    @Test
    fun tapTempoButtonDisplays() {
        composeRule.onNodeWithText(activity.getString(R.string.tap_tempo)).assertIsDisplayed()
    }

    @Test
    fun polyrhythmIsInsideMetronomeContainerNotBottomNav() {
        composeRule.onNodeWithTag("metronome_mode_metronome").assertIsDisplayed()
        composeRule.onNodeWithTag("metronome_mode_polyrhythm").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(activity.getString(R.string.polyrhythm))
            .assertDoesNotExist()
    }

    @Test
    fun switchingToPolyrhythmStopsMetronome() {
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        composeRule.onNodeWithTag("metronome_mode_polyrhythm").performClick()

        val fake = audio as FakeAudioPlayerService
        assertEquals(1, fake.startCount)
        assertEquals(1, fake.stopCount)
    }

    @Test
    fun switchingToMetronomeStopsPolyrhythm() {
        composeRule.onNodeWithTag("metronome_mode_polyrhythm").performClick()
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        composeRule.onNodeWithTag("metronome_mode_metronome").performClick()

        val fake = audio as FakeAudioPlayerService
        assertEquals(1, fake.polyrhythmStartCount)
        assertEquals(1, fake.polyrhythmStopCount)
    }

    @Test
    fun alwaysUseDarkThemeSettingPersists() {
        composeRule.onNodeWithText(activity.getString(R.string.settings)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.appearance)).assertIsDisplayed()
        composeRule.onNodeWithTag("always_use_dark_theme_switch").performClick()

        assertFalse(FakeAppPreferences.instance.alwaysUseDarkTheme)
    }
}
