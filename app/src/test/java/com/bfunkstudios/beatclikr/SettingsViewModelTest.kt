package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.IFlashlightService
import com.bfunkstudios.beatclikr.ui.FlashlightSettingsAction
import com.bfunkstudios.beatclikr.ui.FlashlightSettingsDialog
import com.bfunkstudios.beatclikr.ui.SettingsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private lateinit var prefs: IAppPreferences
    private lateinit var flashlight: IFlashlightService
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        flashlight = mockk(relaxed = true)
        every { flashlight.hasFlashlight } returns true
        every { prefs.useFlashlight } returns false
        every { prefs.useVibration } returns false
        every { prefs.alwaysUseDarkTheme } returns false
        every { prefs.muteMetronome } returns false
        every { prefs.keepScreenAwake } returns false
        every { prefs.sixteenthAlternate } returns false
        every { prefs.practiceReminderEnabled } returns false
        every { prefs.practiceReminderHour } returns 9
        every { prefs.practiceReminderMinute } returns 0
        every { prefs.instantBeatSound } returns SoundFile.CLICK_HI
        every { prefs.instantRhythmSound } returns SoundFile.CLICK_LO
        every { prefs.playlistBeatSound } returns SoundFile.CLICK_HI
        every { prefs.playlistRhythmSound } returns SoundFile.CLICK_LO
        every { prefs.polyrhythmBeatSound } returns SoundFile.CLICK_HI
        every { prefs.polyrhythmRhythmSound } returns SoundFile.CLICK_LO
        viewModel = SettingsViewModel(prefs, flashlight)
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

    @Test
    fun `practice reminder settings load from prefs`() {
        assertFalse(viewModel.practiceReminderEnabled)
    }

    @Test
    fun `updatePracticeReminderEnabled saves to prefs`() {
        viewModel.updatePracticeReminderEnabled(true)

        assertTrue(viewModel.practiceReminderEnabled)
        verify { prefs.practiceReminderEnabled = true }
    }

    @Test
    fun `updatePracticeReminderTime saves clamped values to prefs`() {
        viewModel.updatePracticeReminderTime(25, -1)

        assertEquals(23, viewModel.practiceReminderHour)
        assertEquals(0, viewModel.practiceReminderMinute)
        verify { prefs.practiceReminderHour = 23 }
        verify { prefs.practiceReminderMinute = 0 }
    }

    @Test
    fun `syncFlashlightStateOnEnter disables saved flashlight when permission is missing`() {
        every { prefs.useFlashlight } returns true
        viewModel = SettingsViewModel(prefs, flashlight)

        val changed = viewModel.syncFlashlightStateOnEnter(hasCameraPermission = false)

        assertTrue(changed)
        assertFalse(viewModel.useFlashlight)
        verify { prefs.useFlashlight = false }
    }

    @Test
    fun `syncFlashlightStateOnEnter disables saved flashlight and shows dialog when flash is unavailable`() {
        every { prefs.useFlashlight } returns true
        every { flashlight.hasFlashlight } returns false
        viewModel = SettingsViewModel(prefs, flashlight)

        val changed = viewModel.syncFlashlightStateOnEnter(hasCameraPermission = true)

        assertTrue(changed)
        assertFalse(viewModel.useFlashlight)
        assertEquals(FlashlightSettingsDialog.Unavailable, viewModel.flashlightDialog)
        verify { prefs.useFlashlight = false }
    }

    @Test
    fun `onFlashlightToggleRequested enables flashlight when permission is granted`() {
        val action = viewModel.onFlashlightToggleRequested(
            enabled = true,
            hasCameraPermission = true
        )

        assertEquals(FlashlightSettingsAction.None, action)
        assertTrue(viewModel.useFlashlight)
        verify { prefs.useFlashlight = true }
    }

    @Test
    fun `onFlashlightToggleRequested requests permission before enabling flashlight`() {
        val action = viewModel.onFlashlightToggleRequested(
            enabled = true,
            hasCameraPermission = false
        )

        assertEquals(FlashlightSettingsAction.RequestPermission, action)
        assertFalse(viewModel.useFlashlight)
    }

    @Test
    fun `onFlashlightToggleRequested shows unavailable dialog when device has no flash`() {
        every { flashlight.hasFlashlight } returns false

        val action = viewModel.onFlashlightToggleRequested(
            enabled = true,
            hasCameraPermission = true
        )

        assertEquals(FlashlightSettingsAction.None, action)
        assertFalse(viewModel.useFlashlight)
        assertEquals(FlashlightSettingsDialog.Unavailable, viewModel.flashlightDialog)
        verify { prefs.useFlashlight = false }
    }

    @Test
    fun `onFlashlightPermissionResult enables flashlight when permission is granted`() {
        viewModel.onFlashlightPermissionResult(granted = true, blocked = false)

        assertTrue(viewModel.useFlashlight)
        verify { prefs.useFlashlight = true }
    }

    @Test
    fun `onFlashlightPermissionResult disables flashlight and shows blocked dialog when permission is blocked`() {
        viewModel.onFlashlightPermissionResult(granted = false, blocked = true)

        assertFalse(viewModel.useFlashlight)
        assertEquals(FlashlightSettingsDialog.PermissionDenied(blocked = true), viewModel.flashlightDialog)
        verify { prefs.useFlashlight = false }
    }

    @Test
    fun `dismissFlashlightDialog clears current dialog`() {
        every { flashlight.hasFlashlight } returns false
        viewModel.onFlashlightToggleRequested(enabled = true, hasCameraPermission = true)

        viewModel.dismissFlashlightDialog()

        assertEquals(null, viewModel.flashlightDialog)
    }
}
