package com.bfunkstudios.beatclikr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.IFlashlightService
import com.bfunkstudios.beatclikr.services.IPracticeReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed interface FlashlightSettingsDialog {
    data object Unavailable : FlashlightSettingsDialog
    data class PermissionDenied(val blocked: Boolean) : FlashlightSettingsDialog
}

sealed interface FlashlightSettingsAction {
    data object None : FlashlightSettingsAction
    data object RequestPermission : FlashlightSettingsAction
}

enum class ReminderPermissionStatus {
    Granted,
    NotDetermined,
    Denied,
    Blocked
}

sealed interface ReminderSettingsAction {
    data object None : ReminderSettingsAction
    data object RequestPermission : ReminderSettingsAction
}

sealed interface ReminderSettingsDialog {
    data class PermissionDenied(val blocked: Boolean) : ReminderSettingsDialog
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: IAppPreferences,
    private val flashlight: IFlashlightService,
    private val reminderScheduler: IPracticeReminderScheduler
) : ViewModel() {

    var useFlashlight by mutableStateOf(prefs.useFlashlight)
        private set

    val hasFlashlight: Boolean
        get() = flashlight.hasFlashlight

    var flashlightDialog by mutableStateOf<FlashlightSettingsDialog?>(null)
        private set

    var useVibration by mutableStateOf(prefs.useVibration)
        private set

    var alwaysUseDarkTheme by mutableStateOf(prefs.alwaysUseDarkTheme)
        private set

    var muteMetronome by mutableStateOf(prefs.muteMetronome)
        private set

    var keepScreenAwake by mutableStateOf(prefs.keepScreenAwake)
        private set

    var sixteenthAlternate by mutableStateOf(prefs.sixteenthAlternate)
        private set

    var practiceReminderEnabled by mutableStateOf(prefs.practiceReminderEnabled)
        private set

    var practiceReminderHour by mutableStateOf(prefs.practiceReminderHour)
        private set

    var practiceReminderMinute by mutableStateOf(prefs.practiceReminderMinute)
        private set

    val practiceReminderNotificationPermissionRequested: Boolean
        get() = prefs.practiceReminderNotificationPermissionRequested

    var notificationsBlockedLocally by mutableStateOf(false)
        private set

    var notificationsDeferredLocally by mutableStateOf(prefs.practiceReminderNotificationsDeferred)
        private set

    var showCrossDeviceReminderPrompt by mutableStateOf(false)
        private set

    var reminderDialog by mutableStateOf<ReminderSettingsDialog?>(null)
        private set

    var exactAlarmsUnavailable by mutableStateOf(false)
        private set

    var metronomeBeatSound by mutableStateOf(prefs.instantBeatSound)
        private set

    var metronomeRhythmSound by mutableStateOf(prefs.instantRhythmSound)
        private set

    var playlistBeatSound by mutableStateOf(prefs.playlistBeatSound)
        private set

    var playlistRhythmSound by mutableStateOf(prefs.playlistRhythmSound)
        private set

    var polyrhythmBeatSound by mutableStateOf(prefs.polyrhythmBeatSound)
        private set

    var polyrhythmRhythmSound by mutableStateOf(prefs.polyrhythmRhythmSound)
        private set

    fun updateUseFlashlight(value: Boolean) {
        useFlashlight = value
        prefs.useFlashlight = value
    }

    fun syncFlashlightStateOnEnter(hasCameraPermission: Boolean): Boolean {
        if (!useFlashlight) return false
        return when {
            !flashlight.hasFlashlight -> {
                updateUseFlashlight(false)
                flashlightDialog = FlashlightSettingsDialog.Unavailable
                true
            }
            !hasCameraPermission -> {
                updateUseFlashlight(false)
                true
            }
            else -> false
        }
    }

    fun onFlashlightToggleRequested(enabled: Boolean, hasCameraPermission: Boolean): FlashlightSettingsAction {
        if (!enabled) {
            updateUseFlashlight(false)
            return FlashlightSettingsAction.None
        }

        if (!flashlight.hasFlashlight) {
            updateUseFlashlight(false)
            flashlightDialog = FlashlightSettingsDialog.Unavailable
            return FlashlightSettingsAction.None
        }

        if (hasCameraPermission) {
            updateUseFlashlight(true)
            return FlashlightSettingsAction.None
        }

        return FlashlightSettingsAction.RequestPermission
    }

    fun onFlashlightPermissionResult(granted: Boolean, blocked: Boolean) {
        if (granted) {
            updateUseFlashlight(true)
        } else {
            updateUseFlashlight(false)
            flashlightDialog = FlashlightSettingsDialog.PermissionDenied(blocked)
        }
    }

    fun dismissFlashlightDialog() {
        flashlightDialog = null
    }

    fun updateUseVibration(value: Boolean) {
        useVibration = value
        prefs.useVibration = value
    }

    fun updateAlwaysUseDarkTheme(value: Boolean) {
        alwaysUseDarkTheme = value
        prefs.alwaysUseDarkTheme = value
    }

    fun updateMuteMetronome(value: Boolean) {
        muteMetronome = value
        prefs.muteMetronome = value
    }

    fun updateKeepScreenAwake(value: Boolean) {
        keepScreenAwake = value
        prefs.keepScreenAwake = value
    }

    fun updateSixteenthAlternate(value: Boolean) {
        sixteenthAlternate = value
        prefs.sixteenthAlternate = value
    }

    fun updatePracticeReminderEnabled(value: Boolean) {
        practiceReminderEnabled = value
        prefs.practiceReminderEnabled = value
    }

    fun syncReminderPermissionState(status: ReminderPermissionStatus) {
        exactAlarmsUnavailable = practiceReminderEnabled && !reminderScheduler.canScheduleExactAlarms()
        if (!practiceReminderEnabled) return
        when (status) {
            ReminderPermissionStatus.Granted -> {
                clearReminderPermissionWarnings()
                reminderScheduler.reschedule()
            }
            ReminderPermissionStatus.NotDetermined -> {
                notificationsBlockedLocally = false
                if (!notificationsDeferredLocally) {
                    showCrossDeviceReminderPrompt = true
                }
            }
            ReminderPermissionStatus.Denied,
            ReminderPermissionStatus.Blocked -> {
                notificationsBlockedLocally = true
                clearReminderDeferral()
            }
        }
    }

    fun onPracticeReminderToggleRequested(
        enabled: Boolean,
        status: ReminderPermissionStatus
    ): ReminderSettingsAction {
        if (!enabled) {
                updatePracticeReminderEnabled(false)
                clearReminderPermissionWarnings()
                reminderScheduler.cancel()
                return ReminderSettingsAction.None
            }

        return when (status) {
            ReminderPermissionStatus.Granted -> {
                updatePracticeReminderEnabled(true)
                clearReminderPermissionWarnings()
                exactAlarmsUnavailable = !reminderScheduler.canScheduleExactAlarms()
                reminderScheduler.reschedule()
                ReminderSettingsAction.None
            }
            ReminderPermissionStatus.NotDetermined,
            ReminderPermissionStatus.Denied -> {
                ReminderSettingsAction.RequestPermission
            }
            ReminderPermissionStatus.Blocked -> {
                updatePracticeReminderEnabled(false)
                notificationsBlockedLocally = true
                clearReminderDeferral()
                reminderDialog = ReminderSettingsDialog.PermissionDenied(blocked = true)
                ReminderSettingsAction.None
            }
        }
    }

    fun onPracticeReminderPermissionResult(granted: Boolean, blocked: Boolean) {
        prefs.practiceReminderNotificationPermissionRequested = true
        if (granted) {
            updatePracticeReminderEnabled(true)
            clearReminderPermissionWarnings()
            exactAlarmsUnavailable = !reminderScheduler.canScheduleExactAlarms()
            reminderScheduler.reschedule()
        } else {
            updatePracticeReminderEnabled(false)
            clearReminderDeferral()
            notificationsBlockedLocally = blocked
            reminderDialog = ReminderSettingsDialog.PermissionDenied(blocked)
            reminderScheduler.cancel()
        }
    }

    fun allowRemindersFromOtherDevice(status: ReminderPermissionStatus): ReminderSettingsAction {
        showCrossDeviceReminderPrompt = false
        return when (status) {
            ReminderPermissionStatus.Granted -> {
                clearReminderPermissionWarnings()
                reminderScheduler.reschedule()
                ReminderSettingsAction.None
            }
            ReminderPermissionStatus.NotDetermined,
            ReminderPermissionStatus.Denied -> ReminderSettingsAction.RequestPermission
            ReminderPermissionStatus.Blocked -> {
                notificationsBlockedLocally = true
                clearReminderDeferral()
                reminderDialog = ReminderSettingsDialog.PermissionDenied(blocked = true)
                ReminderSettingsAction.None
            }
        }
    }

    fun declineRemindersFromOtherDevice() {
        showCrossDeviceReminderPrompt = false
        notificationsBlockedLocally = false
        notificationsDeferredLocally = true
        prefs.practiceReminderNotificationsDeferred = true
    }

    fun dismissReminderDialog() {
        reminderDialog = null
    }

    fun updatePracticeReminderTime(hour: Int, minute: Int) {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        practiceReminderHour = safeHour
        practiceReminderMinute = safeMinute
        prefs.practiceReminderHour = safeHour
        prefs.practiceReminderMinute = safeMinute
        reminderScheduler.rescheduleIfEnabled()
    }

    fun updateMetronomeBeatSound(value: SoundFile) {
        metronomeBeatSound = value
        prefs.instantBeatSound = value
    }

    fun updateMetronomeRhythmSound(value: SoundFile) {
        metronomeRhythmSound = value
        prefs.instantRhythmSound = value
    }

    fun updatePlaylistBeatSound(value: SoundFile) {
        playlistBeatSound = value
        prefs.playlistBeatSound = value
    }

    fun updatePlaylistRhythmSound(value: SoundFile) {
        playlistRhythmSound = value
        prefs.playlistRhythmSound = value
    }

    fun updatePolyrhythmBeatSound(value: SoundFile) {
        polyrhythmBeatSound = value
        prefs.polyrhythmBeatSound = value
    }

    fun updatePolyrhythmRhythmSound(value: SoundFile) {
        polyrhythmRhythmSound = value
        prefs.polyrhythmRhythmSound = value
    }

    private fun clearReminderPermissionWarnings() {
        notificationsBlockedLocally = false
        showCrossDeviceReminderPrompt = false
        clearReminderDeferral()
    }

    private fun clearReminderDeferral() {
        notificationsDeferredLocally = false
        prefs.practiceReminderNotificationsDeferred = false
    }
}
