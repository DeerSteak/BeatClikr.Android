package com.bfunkstudios.beatclikr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.di.AppModule
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Before
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
    abstract class TestModule {
        @Binds @Singleton
        abstract fun bindAudio(fake: FakeAudioPlayerService): IAudioPlayerService

        @Binds @Singleton
        abstract fun bindPrefs(fake: FakeAppPreferences): IAppPreferences
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
}
