package com.bfunkstudios.beatclikr

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.room.Room
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
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
import com.bfunkstudios.beatclikr.services.IPracticeReminderScheduler
import com.bfunkstudios.beatclikr.services.PlaybackObservation
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
import java.util.Locale
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        fun provideFakeAudio(): FakeAudioPlayerService = FakeAudioPlayerService()

        @Provides
        fun provideAudio(fake: FakeAudioPlayerService): IAudioPlayerService = fake

        @Provides
        fun providePlaybackObservation(fake: FakeAudioPlayerService): PlaybackObservation = fake

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

        composeRule.onNodeWithTag("metronome_mode_polyrhythm").performClick()
        fake.publishPlaying(PlaybackMode.POLYRHYTHM, AudioOutputRoute.BLUETOOTH)
        composeRule.onNodeWithTag("bluetooth_latency_warning").assertIsDisplayed()

        fake.stopPlayback()
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

        composeRule.onNodeWithTag("metronome_mode_polyrhythm").performClick()
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
    fun keepScreenOnRequiresPreferenceVisibilityAndPlaying() {
        val fake = audio as FakeAudioPlayerService
        fake.publishPlaying(PlaybackMode.STANDARD)
        composeRule.waitForIdle()
        assertFalse(activity.isKeepingScreenOn())

        FakeAppPreferences.instance.keepScreenAwake = true
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.waitUntil { activity.isKeepingScreenOn() }

        fake.startMetronome(120f, 4, null, false)
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

        fake.stopPlayback()
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
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onAllNodesWithContentDescription(
                    activity.getString(R.string.polyrhythm)
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
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
    fun compactModeReplacementStopsHiddenModeOnceAndStartsAtFreshSession() {
        val fake = audio as FakeAudioPlayerService
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()
        val standardSession = fake.currentSessionId()

        composeRule.onNodeWithTag("metronome_mode_polyrhythm").performClick()
        composeRule.onNodeWithText(activity.getString(R.string.play)).performClick()

        assertEquals(1, fake.stopCount)
        assertEquals(1, fake.polyrhythmStartCount)
        assertNotEquals(standardSession, fake.currentSessionId())
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
        composeRule.onNodeWithContentDescription(activity.getString(title)).performClick()
        composeRule.waitForIdle()
    }

    private fun FakeAudioPlayerService.currentSessionId() =
        (transportState.value as PlaybackTransportState.SessionState).context.sessionId
}
