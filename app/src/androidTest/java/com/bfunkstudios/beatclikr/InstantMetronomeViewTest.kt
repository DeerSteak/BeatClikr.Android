package com.bfunkstudios.beatclikr

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import android.view.View
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performScrollTo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.ui.BeatClikrApp
import com.bfunkstudios.beatclikr.ui.MetronomeView
import com.bfunkstudios.beatclikr.ui.theme.BeatClikrTheme
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.Playlist
import com.bfunkstudios.beatclikr.data.PlaylistEntry
import com.bfunkstudios.beatclikr.data.PlaylistRepository
import com.bfunkstudios.beatclikr.data.PlaylistRepositoryImpl
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepository
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepositoryImpl
import com.bfunkstudios.beatclikr.data.Song
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
import com.bfunkstudios.beatclikr.services.IPracticeReminderScheduler
import com.bfunkstudios.beatclikr.services.PlaybackObservation
import com.bfunkstudios.beatclikr.services.PlaybackLifecycleObservation
import com.bfunkstudios.beatclikr.services.AudioOutputRoute
import com.bfunkstudios.beatclikr.services.PlaybackFailureReason
import com.bfunkstudios.beatclikr.services.PlaybackInterruptionReason
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackTransportState
import com.bfunkstudios.beatclikr.services.SecondaryOutputCoordinator
import com.bfunkstudios.beatclikr.services.SecondaryOutputObservation
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
import kotlinx.coroutines.runBlocking
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    @Inject lateinit var playlistDao: PlaylistDao
    @Inject lateinit var songDao: SongDao

    @Module
    @InstallIn(SingletonComponent::class)
    object TestModule {
        @Provides @Singleton
        fun provideFakeAudio(): FakeAudioPlayerService = FakeAudioPlayerService()

        @Provides
        fun provideAudio(fake: FakeAudioPlayerService): IAudioPlayerService = fake

        @Provides
        fun providePlaybackObservation(fake: FakeAudioPlayerService): PlaybackObservation = fake

        @Provides @Singleton
        fun providePlaybackLifecycleObservation(): PlaybackLifecycleObservation =
            FakePlaybackLifecycleObservation()

        @Provides @Singleton
        fun provideFlashlight(): IFlashlightService = FakeFlashlightService()

        @Provides @Singleton
        fun provideHaptics(): IHapticFeedbackService = FakeHapticFeedbackService()

        @Provides @Singleton
        fun provideSecondaryOutputCoordinator(
            playback: PlaybackObservation,
            prefs: IAppPreferences,
            flashlight: IFlashlightService,
            haptics: IHapticFeedbackService,
            @ApplicationScope scope: CoroutineScope
        ): SecondaryOutputCoordinator =
            SecondaryOutputCoordinator(playback, prefs, flashlight, haptics, scope)

        @Provides
        fun provideSecondaryOutputObservation(
            coordinator: SecondaryOutputCoordinator
        ): SecondaryOutputObservation = coordinator

        @Provides @Singleton
        fun providePracticeReminderScheduler(): IPracticeReminderScheduler = FakePracticeReminderScheduler()

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

    @After
    fun restoreOrientation() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private val activity get() = composeRule.activity

    @Test
    fun bpmDisplaysOnLaunch() {
        composeRule.onNodeWithText("60").assertIsDisplayed()
    }

    @Test
    fun launchAndActivityRecreationDoNotStartPlayback() {
        val fake = audio as FakeAudioPlayerService
        assertEquals(0, fake.startCount)
        assertEquals(0, fake.polyrhythmStartCount)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        assertEquals(0, fake.startCount)
        assertEquals(0, fake.polyrhythmStartCount)
    }

    @Test
    fun activityRecreationPreservesActivePlaybackWithoutRestart() {
        val fake = audio as FakeAudioPlayerService
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        assertEquals(1, fake.startCount)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        assertEquals(0, fake.stopCount)
        assertEquals(0, fake.polyrhythmStopCount)
        assertEquals(1, fake.startCount)
        assertEquals(0, fake.polyrhythmStartCount)
        composeRule.onNodeWithText(activity.getString(R.string.pause)).assertIsDisplayed()
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
    fun bluetoothWarningTracksAuthoritativeRouteInBothModes() {
        val fake = audio as FakeAudioPlayerService
        fake.publishPlaying(PlaybackMode.STANDARD, AudioOutputRoute.BLUETOOTH)
        composeRule.onNodeWithTag("bluetooth_latency_warning").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            activity.getString(R.string.bluetooth_latency_warning)
        ).assertIsDisplayed()

        fake.publishPlaying(PlaybackMode.STANDARD, AudioOutputRoute.BUILT_IN)
        composeRule.onNodeWithTag("bluetooth_latency_warning").assertDoesNotExist()

        selectMetronomeMode(polyrhythm = true)
        fake.publishPlaying(PlaybackMode.POLYRHYTHM, AudioOutputRoute.BLUETOOTH)
        composeRule.onNodeWithTag("bluetooth_latency_warning").assertIsDisplayed()

        fake.stopPlaybackForTest()
        composeRule.onNodeWithTag("bluetooth_latency_warning").assertDoesNotExist()
    }

    @Test
    fun playbackDiagnosticsAreVisibleAndSuccessfulStartClearsThem() {
        val fake = audio as FakeAudioPlayerService
        val cases = listOf(
            PlaybackFailureReason.AudioFocusUnavailable to R.string.playback_focus_unavailable,
            PlaybackFailureReason.RouteUnavailable to R.string.playback_route_unavailable,
            PlaybackFailureReason.StreamStart("stream start rejected") to
                R.string.playback_stream_start_failed,
            PlaybackFailureReason.Engine("RENDER: INTERNAL_ERROR") to
                R.string.playback_engine_failed
        )
        cases.forEach { (reason, message) ->
            fake.publishFailed(PlaybackMode.STANDARD, reason)
            composeRule.onNodeWithText(activity.getString(message)).assertIsDisplayed()
        }

        fake.publishInterrupted(
            PlaybackMode.STANDARD,
            PlaybackInterruptionReason.RouteChanged(
                AudioOutputRoute.BUILT_IN,
                AudioOutputRoute.USB
            )
        )
        composeRule.onNodeWithText(
            activity.getString(
                R.string.playback_route_changed,
                activity.getString(R.string.audio_route_built_in),
                activity.getString(R.string.audio_route_usb)
            )
        ).assertIsDisplayed()

        fake.publishPlaying(PlaybackMode.STANDARD)
        composeRule.onNodeWithTag("playback_diagnostic").assertDoesNotExist()

        selectMetronomeMode(polyrhythm = true)
        fake.publishFailed(PlaybackMode.POLYRHYTHM, PlaybackFailureReason.RouteUnavailable)
        composeRule.onNodeWithText(
            activity.getString(R.string.playback_route_unavailable)
        ).assertIsDisplayed()
        fake.publishPlaying(PlaybackMode.POLYRHYTHM)
        composeRule.onNodeWithTag("playback_diagnostic").assertDoesNotExist()
    }

    @Test
    fun spanishRouteChangeMessageUsesLocalizedRouteLabels() {
        val configuration = Configuration(activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("es"))
        }
        val localized = activity.createConfigurationContext(configuration)
        val message = localized.getString(
            R.string.playback_route_changed,
            localized.getString(R.string.audio_route_built_in),
            localized.getString(R.string.audio_route_bluetooth)
        )

        assertEquals(
            "La reproducción se detuvo porque la salida de audio cambió de " +
                "altavoz integrado a Bluetooth.",
            message
        )
        assertFalse(message.contains("BUILT_IN"))
    }

    @Test
    fun pseudolocalesLongStringsPluralsAndRtlResolve() {
        val accentedConfiguration = Configuration(activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("en-XA"))
            fontScale = 2f
        }
        val accented = activity.createConfigurationContext(accentedConfiguration)
        assertTrue(accented.getString(R.string.reminder_operation_failed).isNotBlank())
        assertTrue(accented.resources.getQuantityString(R.plurals.song_count, 2, 2).contains("2"))

        val rtlConfiguration = Configuration(activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar-XB"))
        }
        val rtl = activity.createConfigurationContext(rtlConfiguration)
        assertEquals(View.LAYOUT_DIRECTION_RTL, rtl.resources.configuration.layoutDirection)
        assertTrue(rtl.getString(R.string.playback_status_preparing).isNotBlank())
    }

    @Test
    fun pseudolocaleRtlAndTwoTimesFontRenderWithReachableTransport() {
        val configuration = Configuration(activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar-XB"))
        }
        val localized = activity.createConfigurationContext(configuration)
        activity.setContent {
            val viewModel = hiltViewModel<com.bfunkstudios.beatclikr.ui.MetronomeViewModel>()
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(
                    density = activity.resources.displayMetrics.density,
                    fontScale = 2f
                )
            ) {
                BeatClikrTheme { MetronomeView(viewModel = viewModel) }
            }
        }

        composeRule.onNodeWithText(localized.getString(R.string.play))
            .performScrollTo()
            .assertIsDisplayed()
        assertNonemptyScreenshot()
    }

    @Test
    fun compactSplitFoldableAndExpandedWindowSizesRender() {
        listOf(
            320.dp to 480.dp,
            480.dp to 320.dp,
            600.dp to 500.dp,
            840.dp to 900.dp
        ).forEach { (width, height) ->
            activity.setContent {
                BeatClikrTheme {
                    Box(Modifier.requiredSize(width, height)) { BeatClikrApp() }
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(activity.getString(R.string.play))
                .performScrollTo()
                .assertIsDisplayed()
            captureScreenshot()
        }
    }

    @Test
    fun keepScreenOnRequiresPreferenceVisibilityAndPlaying() {
        val fake = audio as FakeAudioPlayerService
        fake.publishPlaying(PlaybackMode.STANDARD)
        composeRule.waitForIdle()
        assertFalse(activity.isKeepingScreenOn())

        FakeAppPreferences.instance.keepScreenAwake = true
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.waitUntil { activity.isKeepingScreenOn() }

        fake.startMetronomeForTest(120f, 4)
        composeRule.waitUntil { !activity.isKeepingScreenOn() }

        fake.publishPlaying(PlaybackMode.STANDARD)
        composeRule.waitUntil { activity.isKeepingScreenOn() }

        val playing = fake.transportState.value as PlaybackTransportState.Playing
        fake.transportState.value = PlaybackTransportState.Stopping(playing.context)
        composeRule.waitUntil { !activity.isKeepingScreenOn() }

        fake.publishPlaying(PlaybackMode.STANDARD)
        fake.publishInterrupted(
            PlaybackMode.STANDARD,
            PlaybackInterruptionReason.AudioFocusLost
        )
        composeRule.waitUntil { !activity.isKeepingScreenOn() }

        fake.publishPlaying(PlaybackMode.STANDARD)
        composeRule.waitUntil { activity.isKeepingScreenOn() }

        val resumedActivity = activity
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitUntil { !resumedActivity.isKeepingScreenOn() }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitUntil { activity.isKeepingScreenOn() }

        fake.publishFailed(
            PlaybackMode.STANDARD,
            PlaybackFailureReason.Engine("render failed")
        )
        composeRule.waitUntil { !activity.isKeepingScreenOn() }
    }

    @Test
    fun keepScreenOnSurvivesRecreationOnlyForPlayingSession() {
        val fake = audio as FakeAudioPlayerService
        FakeAppPreferences.instance.keepScreenAwake = true
        fake.publishPlaying(PlaybackMode.STANDARD)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil { activity.isKeepingScreenOn() }

        fake.stopPlaybackForTest()
        composeRule.waitUntil { !activity.isKeepingScreenOn() }
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
    fun criticalPerformanceControlsExposeActionsAndLabels() {
        composeRule.onNodeWithContentDescription(
            activity.getString(R.string.decrease_value, activity.getString(R.string.bpm))
        ).assert(hasClickAction())
        composeRule.onNodeWithContentDescription(
            activity.getString(R.string.increase_value, activity.getString(R.string.bpm))
        ).assert(hasClickAction())
        composeRule.onNodeWithContentDescription(
            activity.getString(R.string.tap_tempo),
            substring = true
        ).assert(hasClickAction())
        composeRule.onNodeWithText(activity.getString(R.string.play)).assert(hasClickAction())
    }

    @Test
    fun clickableParentsHaveLabelsAndMinimumTouchTargets() {
        assertLabeledTouchTargets()
        listOf(R.string.tab_library, R.string.tab_playlist, R.string.tab_history, R.string.settings)
            .forEach { destination ->
                navigateTo(destination)
                assertLabeledTouchTargets()
            }
    }

    @Test
    fun keyboardTabMovesFocusFromTransportControl() {
        composeRule.onRoot().performKeyInput { pressKey(Key.Tab) }
        composeRule.waitForIdle()

        composeRule.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test
    fun authoritativePlaybackStatusIsVisibleWithoutBeatAnnouncements() {
        val fake = audio as FakeAudioPlayerService
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.playback_status_preparing)).assertIsDisplayed()

        fake.publishPlaying(PlaybackMode.STANDARD)

        composeRule.onNodeWithText(activity.getString(R.string.playback_status_playing)).assertIsDisplayed()
    }

    @Test
    fun criticalLayoutsThemesAndStatesProduceScreenshots() {
        val idle = captureScreenshot()
        (audio as FakeAudioPlayerService).publishPlaying(PlaybackMode.STANDARD)
        composeRule.onNodeWithText(activity.getString(R.string.playback_status_playing))
            .assertIsDisplayed()
        val playing = captureScreenshot()
        assertScreenshotsDiffer(idle, playing)

        navigateTo(R.string.settings)
        val firstTheme = captureScreenshot()
        composeRule.onNodeWithTag("always_use_dark_theme_switch").performClick()
        composeRule.waitForIdle()
        val secondTheme = captureScreenshot()
        assertScreenshotsDiffer(firstTheme, secondTheme)

        val initialOrientation = activity.resources.configuration.orientation
        activity.requestedOrientation = if (initialOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitUntil(10_000) {
            activity.resources.configuration.orientation != initialOrientation
        }
        val rotated = captureScreenshot()
        assertTrue(rotated.width != secondTheme.width || rotated.height != secondTheme.height)

        val originalAnimatorScale = shell("settings get global animator_duration_scale").trim().ifBlank { "1.0" }
        try {
            shell("settings put global animator_duration_scale 0")
            composeRule.waitForIdle()
            captureScreenshot()
        } finally {
            restoreSetting("global", "animator_duration_scale", originalAnimatorScale)
        }
        FakeAppPreferences.instance.alwaysUseDarkTheme = false
    }

    @Test
    fun polyrhythmNavigationMatchesWindowSize() {
        if (activity.resources.configuration.screenWidthDp < 600) {
            composeRule.onNodeWithTag("metronome_mode_metronome").assertIsDisplayed()
            composeRule.onNodeWithTag("metronome_mode_polyrhythm").assertIsDisplayed()
            composeRule
                .onNodeWithContentDescription(activity.getString(R.string.polyrhythm))
                .assertDoesNotExist()
        } else {
            composeRule.onNodeWithTag("metronome_mode_metronome").assertDoesNotExist()
            composeRule.onNodeWithTag("metronome_mode_polyrhythm").assertDoesNotExist()
            composeRule.onNodeWithContentDescription(
                activity.getString(R.string.polyrhythm),
                useUnmergedTree = true
            ).assertIsDisplayed()
        }
    }

    @Test
    fun switchingToPolyrhythmStopsMetronome() {
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        selectMetronomeMode(polyrhythm = true)

        val fake = audio as FakeAudioPlayerService
        assertEquals(1, fake.startCount)
        assertEquals(1, fake.stopCount)
    }

    @Test
    fun switchingToMetronomeStopsPolyrhythm() {
        selectMetronomeMode(polyrhythm = true)
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        selectMetronomeMode(polyrhythm = false)

        val fake = audio as FakeAudioPlayerService
        assertEquals(1, fake.polyrhythmStartCount)
        assertEquals(1, fake.polyrhythmStopCount)
    }

    @Test
    fun compactTopLevelDestinationsIssueOneGlobalStopForEveryPlaybackKind() {
        val destinations = listOf(
            R.string.tab_library,
            R.string.tab_playlist,
            R.string.tab_history,
            R.string.tab_settings
        )

        assertTopLevelStopMatrix(destinations)
        destinations.forEach { destination ->
            navigateTo(destination)
            assertOneStopWhenNavigatingTo(R.string.tab_instant, PlaybackMode.STANDARD)
        }
    }

    @Test
    fun expandedTopLevelDestinationsIssueOneGlobalStopForEveryPlaybackKind() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val enteredLandscape = runCatching {
            composeRule.waitUntil(10_000) {
                activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
        }.isSuccess
        assumeTrue(enteredLandscape)
        assumeTrue(activity.resources.configuration.screenWidthDp >= 600)
        composeRule.onNodeWithContentDescription(
            activity.getString(R.string.polyrhythm),
            useUnmergedTree = true
        ).assertIsDisplayed()
        val destinations = listOf(
            R.string.polyrhythm,
            R.string.tab_library,
            R.string.tab_playlist,
            R.string.tab_history,
            R.string.tab_settings
        )

        assertTopLevelStopMatrix(destinations)
        destinations.forEach { destination ->
            navigateTo(destination)
            assertOneStopWhenNavigatingTo(R.string.tab_instant, PlaybackMode.STANDARD)
        }
    }

    @Test
    fun modeReplacementStopsHiddenModeOnceAndStartsAtFreshSession() {
        val fake = audio as FakeAudioPlayerService
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        val standardSession = fake.currentSessionId()

        selectMetronomeMode(polyrhythm = true)
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()

        assertEquals(1, fake.stopCount)
        assertEquals(1, fake.polyrhythmStartCount)
        assertNotEquals(standardSession, fake.currentSessionId())
    }

    @Test
    fun internalEditorsPickersSheetsAndFocusNavigationDoNotStopPlayback() {
        val fake = audio as FakeAudioPlayerService
        val song = Song.instantSong().copy(title = "Internal Navigation Song")
        val playlist = Playlist(name = "Internal Navigation Playlist")
        runBlocking {
            songDao.upsert(song)
            playlistDao.upsertPlaylist(playlist)
            playlistDao.upsertEntry(
                PlaylistEntry(playlistId = playlist.id, songId = song.id, sequence = 0)
            )
        }

        navigateTo(R.string.tab_library)
        fake.publishPlaying(PlaybackMode.STANDARD)
        fake.resetCallCounts()
        composeRule.onNodeWithContentDescription(activity.getString(R.string.add_song)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.song_detail)).assertIsDisplayed()
        assertEquals(0, fake.stopCount + fake.polyrhythmStopCount)
        composeRule.onNodeWithText(activity.getString(R.string.cancel)).performClick()

        fake.stopPlaybackForTest()
        fake.resetCallCounts()
        navigateTo(R.string.tab_playlist)
        fake.publishPlaying(PlaybackMode.STANDARD)
        fake.resetCallCounts()
        composeRule.onNodeWithText(playlist.name).performClick()
        composeRule.onNodeWithText(song.title).assertIsDisplayed()
        assertEquals(0, fake.stopCount + fake.polyrhythmStopCount)

        composeRule.onNodeWithContentDescription(activity.getString(R.string.add_song)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.add_song)).assertIsDisplayed()
        assertEquals(0, fake.stopCount + fake.polyrhythmStopCount)
        composeRule.onNodeWithTag("playlist_song_picker_${song.id}").performClick()

        composeRule.onNodeWithText(activity.getString(R.string.edit)).performClick()
        assertEquals(0, fake.stopCount + fake.polyrhythmStopCount)
        composeRule.onNodeWithText(activity.getString(R.string.done)).performClick()
        composeRule.onNodeWithContentDescription(activity.getString(R.string.focus_view)).performClick()
        assertEquals(0, fake.stopCount + fake.polyrhythmStopCount)
    }

    @Test
    fun librarySongToTopLevelDestinationIssuesExactlyOneGlobalStop() {
        val fake = audio as FakeAudioPlayerService
        val song = Song.instantSong().copy(title = "Top Level Navigation Song")
        runBlocking { songDao.upsert(song) }

        navigateTo(R.string.tab_library)
        composeRule.onNodeWithText(song.title).performClick()
        fake.publishPlaying(PlaybackMode.STANDARD)
        fake.resetCallCounts()

        navigateTo(R.string.tab_playlist)

        assertEquals(1, fake.stopCount)
        assertEquals(PlaybackTransportState.Idle, fake.transportState.value)
    }

    @Test
    fun alwaysUseDarkThemeSettingPersists() {
        composeRule.onNodeWithText(activity.getString(R.string.settings)).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.appearance)).assertIsDisplayed()
        composeRule.onNodeWithTag("always_use_dark_theme_switch").performClick()

        assertFalse(FakeAppPreferences.instance.alwaysUseDarkTheme)
    }

    private fun MainActivity.isKeepingScreenOn(): Boolean =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    private fun assertNonemptyScreenshot() {
        captureScreenshot()
    }

    private fun captureScreenshot(): ImageBitmap {
        val image = composeRule.onRoot().captureToImage()
        assertTrue(image.width > 0)
        assertTrue(image.height > 0)
        return image
    }

    private fun assertScreenshotsDiffer(first: ImageBitmap, second: ImageBitmap) {
        val firstPixels = first.toPixelMap()
        val secondPixels = second.toPixelMap()
        val width = minOf(first.width, second.width)
        val height = minOf(first.height, second.height)
        var sampled = 0
        var changed = 0
        for (y in 0 until height step 12) {
            for (x in 0 until width step 12) {
                sampled++
                if (firstPixels[x, y] != secondPixels[x, y]) changed++
            }
        }
        assertTrue("Screenshots did not materially differ", changed.toFloat() / sampled >= 0.001f)
    }

    private fun assertLabeledTouchTargets() {
        val density = activity.resources.displayMetrics.density
        composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val hasLabel = node.config.contains(SemanticsProperties.ContentDescription) ||
                node.config.contains(SemanticsProperties.Text)
            val touchBounds = node.touchBoundsInRoot
            val details = "${node.config}, touchBounds=$touchBounds"
            assertTrue("Clickable node has no accessible label: $details", hasLabel)
            if (touchBounds.width == 0f || touchBounds.height == 0f) return@forEach
            assertTrue("Touch target is narrower than 48dp: $details", touchBounds.width / density >= 48f)
            assertTrue("Touch target is shorter than 48dp: $details", touchBounds.height / density >= 48f)
        }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun restoreSetting(namespace: String, key: String, value: String) {
        if (value == "null") shell("settings delete $namespace $key")
        else shell("settings put $namespace $key $value")
    }

    private fun assertTopLevelStopMatrix(destinations: List<Int>) {
        destinations.forEach { destination ->
            listOf(PlaybackMode.STANDARD, PlaybackMode.POLYRHYTHM).forEach { mode ->
                assertOneStopWhenNavigatingTo(destination, mode)
                navigateTo(R.string.tab_instant)
            }
        }
    }

    private fun assertOneStopWhenNavigatingTo(destination: Int, mode: PlaybackMode) {
        val fake = audio as FakeAudioPlayerService
        fake.resetCallCounts()
        fake.publishPlaying(mode)

        navigateTo(destination)

        assertEquals(1, fake.stopCount + fake.polyrhythmStopCount)
        assertEquals(PlaybackTransportState.Idle, fake.transportState.value)
    }

    private fun navigateTo(title: Int) {
        composeRule.onNodeWithContentDescription(
            activity.getString(title),
            useUnmergedTree = true
        ).performClick()
        composeRule.waitForIdle()
    }

    private fun selectMetronomeMode(polyrhythm: Boolean) {
        val tag = if (polyrhythm) "metronome_mode_polyrhythm" else "metronome_mode_metronome"
        if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
        } else {
            navigateTo(if (polyrhythm) R.string.polyrhythm else R.string.tab_instant)
        }
    }

    private fun FakeAudioPlayerService.currentSessionId() =
        (transportState.value as PlaybackTransportState.SessionState).context.sessionId
}
