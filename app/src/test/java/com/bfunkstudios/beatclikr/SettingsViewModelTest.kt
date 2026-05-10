package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.ui.SettingsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private lateinit var prefs: IAppPreferences
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        every { prefs.useFlashlight } returns false
        every { prefs.useVibration } returns false
        every { prefs.alwaysUseDarkTheme } returns false
        every { prefs.muteMetronome } returns false
        every { prefs.keepScreenAwake } returns false
        every { prefs.sixteenthAlternate } returns false
        every { prefs.instantBeatSound } returns SoundFile.CLICK_HI
        every { prefs.instantRhythmSound } returns SoundFile.CLICK_LO
        every { prefs.playlistBeatSound } returns SoundFile.CLICK_HI
        every { prefs.playlistRhythmSound } returns SoundFile.CLICK_LO
        every { prefs.polyrhythmBeatSound } returns SoundFile.CLICK_HI
        every { prefs.polyrhythmRhythmSound } returns SoundFile.CLICK_LO
        viewModel = SettingsViewModel(prefs)
    }

    @Test
    fun `always use dark theme loads from prefs`() {
        assertFalse(viewModel.alwaysUseDarkTheme)
    }

    @Test
    fun `updateAlwaysUseDarkTheme saves to prefs`() {
        viewModel.updateAlwaysUseDarkTheme(true)

        assertTrue(viewModel.alwaysUseDarkTheme)
        verify { prefs.alwaysUseDarkTheme = true }
    }
}
