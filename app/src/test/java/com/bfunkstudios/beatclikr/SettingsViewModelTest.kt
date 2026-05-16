package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.IFlashlightService
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.IPracticeReminderScheduler
import com.bfunkstudios.beatclikr.ui.FlashlightSettingsDialog
import com.bfunkstudios.beatclikr.ui.ReminderPermissionStatus
import com.bfunkstudios.beatclikr.ui.ReminderSettingsAction
import com.bfunkstudios.beatclikr.ui.ReminderSettingsDialog
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
    private lateinit var audioPlayerService: IAudioPlayerService
    private lateinit var reminderScheduler: IPracticeReminderScheduler
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        flashlight = mockk(relaxed = true)
        audioPlayerService = mockk(relaxed = true)
        reminderScheduler = mockk(relaxed = true)
        every { flashlight.hasFlashlight } returns true
        every { prefs.useFlashlight } returns false
        every { prefs.useVibration } returns false
        every { prefs.alwaysUseDarkTheme } returns false
        every { prefs.muteMetronome } returns false
        every { prefs.keepScreenAwake } returns false
        every { prefs.sixteenthAlternate } returns false
        every { prefs.useAudioTrack } returns false
        every { prefs.soundBank } returns SoundBank.SYNTH
        every { prefs.practiceReminderEnabled } returns false
        every { prefs.practiceReminderHour } returns 9
        every { prefs.practiceReminderMinute } returns 0
        every { prefs.practiceReminderNotificationsDeferred } returns false
        every { prefs.practiceReminderNotificationPermissionRequested } returns false
        every { prefs.instantBeatSound } returns SoundFile.CLICK_HI
        every { prefs.instantRhythmSound } returns SoundFile.CLICK_LO
        every { prefs.playlistBeatSound } returns SoundFile.CLICK_HI
        every { prefs.playlistRhythmSound } returns SoundFile.CLICK_LO
        every { prefs.polyrhythmBeatSound } returns SoundFile.CLICK_HI
        every { prefs.polyrhythmRhythmSound } returns SoundFile.CLICK_LO
        viewModel = SettingsViewModel(prefs, flashlight, audioPlayerService, reminderScheduler)
    }

    @Test
    fun `updateUseAudioTrack saves to prefs`() {
        viewModel.updateUseAudioTrack(true)

        assertTrue(viewModel.useAudioTrack)
        verify { prefs.useAudioTrack = true }
        verify { audioPlayerService.useAudioTrack = true }
    }

    @Test
    fun `updateUseAudioTrack prepares selected files when cached sounds are enabled`() {
        every { prefs.soundBank } returns SoundBank.ACOUSTIC
        viewModel = SettingsViewModel(prefs, flashlight, audioPlayerService, reminderScheduler)

        viewModel.updateUseAudioTrack(true)

        verify {
            audioPlayerService.prepareAudioTrackSounds(
                listOf(
                    SoundFile.CLICK_HI,
                    SoundFile.CLICK_LO,
                    SoundFile.CLICK_HI,
                    SoundFile.CLICK_LO,
                    SoundFile.CLICK_HI,
                    SoundFile.CLICK_LO
                )
            )
        }
    }

    @Test
    fun `updateSoundBank to synth saves to prefs and skips cache`() {
        every { prefs.useAudioTrack } returns true
        every { prefs.soundBank } returns SoundBank.ACOUSTIC
        viewModel = SettingsViewModel(prefs, flashlight, audioPlayerService, reminderScheduler)

        viewModel.updateSoundBank(SoundBank.SYNTH)

        assertEquals(SoundBank.SYNTH, viewModel.soundBank)
        verify { prefs.soundBank = SoundBank.SYNTH }
        verify { audioPlayerService.soundBank = SoundBank.SYNTH }
        verify(exactly = 0) { audioPlayerService.prepareAudioTrackSounds(any()) }
    }

    @Test
    fun `sound changes prepare one file when cached sounds are enabled`() {
        every { prefs.soundBank } returns SoundBank.ACOUSTIC
        viewModel = SettingsViewModel(prefs, flashlight, audioPlayerService, reminderScheduler)

        viewModel.updateMetronomeBeatSound(SoundFile.SNARE)

        verify { audioPlayerService.prepareAudioTrackSounds(listOf(SoundFile.SNARE)) }
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
    fun `onPracticeReminderToggleRequested requests permission before enabling reminders`() {
        val action = viewModel.onPracticeReminderToggleRequested(
            enabled = true,
            status = ReminderPermissionStatus.NotDetermined
        )

        assertEquals(ReminderSettingsAction.RequestPermission, action)
        assertFalse(viewModel.practiceReminderEnabled)
    }

    @Test
    fun `onPracticeReminderToggleRequested enables reminders when notification permission is granted`() {
        val action = viewModel.onPracticeReminderToggleRequested(
            enabled = true,
            status = ReminderPermissionStatus.Granted
        )

        assertEquals(ReminderSettingsAction.None, action)
        assertTrue(viewModel.practiceReminderEnabled)
        verify { prefs.practiceReminderEnabled = true }
    }

    @Test
    fun `onPracticeReminderToggleRequested shows settings dialog when notifications are blocked`() {
        val action = viewModel.onPracticeReminderToggleRequested(
            enabled = true,
            status = ReminderPermissionStatus.Blocked
        )

        assertEquals(ReminderSettingsAction.None, action)
        assertFalse(viewModel.practiceReminderEnabled)
        assertTrue(viewModel.notificationsBlockedLocally)
        assertEquals(ReminderSettingsDialog.PermissionDenied(blocked = true), viewModel.reminderDialog)
        verify { prefs.practiceReminderEnabled = false }
    }

    @Test
    fun `turning reminders off clears local notification warnings`() {
        every { prefs.practiceReminderEnabled } returns true
        every { prefs.practiceReminderNotificationsDeferred } returns true
        viewModel = SettingsViewModel(prefs, flashlight, audioPlayerService, reminderScheduler)
        viewModel.syncReminderPermissionState(ReminderPermissionStatus.Blocked)

        val action = viewModel.onPracticeReminderToggleRequested(
            enabled = false,
            status = ReminderPermissionStatus.Blocked
        )

        assertEquals(ReminderSettingsAction.None, action)
        assertFalse(viewModel.practiceReminderEnabled)
        assertFalse(viewModel.notificationsBlockedLocally)
        assertFalse(viewModel.notificationsDeferredLocally)
        verify { prefs.practiceReminderEnabled = false }
        verify { prefs.practiceReminderNotificationsDeferred = false }
    }

    @Test
    fun `syncReminderPermissionState shows cross device prompt for restored reminders without local permission`() {
        every { prefs.practiceReminderEnabled } returns true
        viewModel = SettingsViewModel(prefs, flashlight, audioPlayerService, reminderScheduler)

        viewModel.syncReminderPermissionState(ReminderPermissionStatus.NotDetermined)

        assertTrue(viewModel.showCrossDeviceReminderPrompt)
        assertFalse(viewModel.notificationsBlockedLocally)
    }

    @Test
    fun `syncReminderPermissionState keeps deferred warning without re-prompting`() {
        every { prefs.practiceReminderEnabled } returns true
        every { prefs.practiceReminderNotificationsDeferred } returns true
        viewModel = SettingsViewModel(prefs, flashlight, audioPlayerService, reminderScheduler)

        viewModel.syncReminderPermissionState(ReminderPermissionStatus.NotDetermined)

        assertTrue(viewModel.notificationsDeferredLocally)
        assertFalse(viewModel.showCrossDeviceReminderPrompt)
    }

    @Test
    fun `declineRemindersFromOtherDevice stores deferred warning state`() {
        every { prefs.practiceReminderEnabled } returns true
        viewModel = SettingsViewModel(prefs, flashlight, audioPlayerService, reminderScheduler)
        viewModel.syncReminderPermissionState(ReminderPermissionStatus.NotDetermined)

        viewModel.declineRemindersFromOtherDevice()

        assertFalse(viewModel.showCrossDeviceReminderPrompt)
        assertTrue(viewModel.notificationsDeferredLocally)
        verify { prefs.practiceReminderNotificationsDeferred = true }
    }

    @Test
    fun `allowRemindersFromOtherDevice requests permission when local status is not determined`() {
        val action = viewModel.allowRemindersFromOtherDevice(ReminderPermissionStatus.NotDetermined)

        assertEquals(ReminderSettingsAction.RequestPermission, action)
        assertFalse(viewModel.showCrossDeviceReminderPrompt)
    }

    @Test
    fun `onPracticeReminderPermissionResult granted enables reminders and clears warnings`() {
        viewModel.declineRemindersFromOtherDevice()

        viewModel.onPracticeReminderPermissionResult(granted = true, blocked = false)

        assertTrue(viewModel.practiceReminderEnabled)
        assertFalse(viewModel.notificationsBlockedLocally)
        assertFalse(viewModel.notificationsDeferredLocally)
        verify { prefs.practiceReminderNotificationPermissionRequested = true }
        verify { prefs.practiceReminderEnabled = true }
        verify { prefs.practiceReminderNotificationsDeferred = false }
    }

    @Test
    fun `onPracticeReminderPermissionResult denied disables reminders and shows dialog`() {
        viewModel.onPracticeReminderPermissionResult(granted = false, blocked = true)

        assertFalse(viewModel.practiceReminderEnabled)
        assertTrue(viewModel.notificationsBlockedLocally)
        assertEquals(ReminderSettingsDialog.PermissionDenied(blocked = true), viewModel.reminderDialog)
        verify { prefs.practiceReminderNotificationPermissionRequested = true }
        verify { prefs.practiceReminderEnabled = false }
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
    fun `syncFlashlightStateOnEnter disables saved flashlight and shows dialog when flash is unavailable`() {
        every { prefs.useFlashlight } returns true
        every { flashlight.hasFlashlight } returns false
        viewModel = SettingsViewModel(prefs, flashlight, audioPlayerService, reminderScheduler)

        val changed = viewModel.syncFlashlightStateOnEnter()

        assertTrue(changed)
        assertFalse(viewModel.useFlashlight)
        assertEquals(FlashlightSettingsDialog.Unavailable, viewModel.flashlightDialog)
        verify { prefs.useFlashlight = false }
    }

    @Test
    fun `onFlashlightToggleRequested enables flashlight when device has flash`() {
        viewModel.onFlashlightToggleRequested(enabled = true)

        assertTrue(viewModel.useFlashlight)
        verify { prefs.useFlashlight = true }
    }

    @Test
    fun `onFlashlightToggleRequested shows unavailable dialog when device has no flash`() {
        every { flashlight.hasFlashlight } returns false

        viewModel.onFlashlightToggleRequested(enabled = true)

        assertFalse(viewModel.useFlashlight)
        assertEquals(FlashlightSettingsDialog.Unavailable, viewModel.flashlightDialog)
        verify { prefs.useFlashlight = false }
    }

    @Test
    fun `dismissFlashlightDialog clears current dialog`() {
        every { flashlight.hasFlashlight } returns false
        viewModel.onFlashlightToggleRequested(enabled = true)

        viewModel.dismissFlashlightDialog()

        assertEquals(null, viewModel.flashlightDialog)
    }
}
