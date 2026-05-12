package com.bfunkstudios.beatclikr.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.ui.components.SectionCard
import com.bfunkstudios.beatclikr.ui.components.SoundPickerRow
import java.util.Calendar

@Composable
fun SettingsView(
    metronomeViewModel: MetronomeViewModel,
    modifier: Modifier = Modifier,
    onAlwaysUseDarkThemeChange: (Boolean) -> Unit = {},
    onKeepScreenAwakeChange: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        if (viewModel.syncFlashlightStateOnEnter()) {
            metronomeViewModel.refreshPlaybackSettings()
        }
        viewModel.syncReminderPermissionState(context.reminderPermissionStatus(viewModel))
    }

    DisposableEffect(lifecycleOwner, context, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncReminderPermissionState(context.reminderPermissionStatus(viewModel))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val reminderPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val blocked = if (granted) {
            false
        } else {
            context.reminderPermissionStatus(
                viewModel = viewModel,
                permissionRequestedOverride = true
            ) == ReminderPermissionStatus.Blocked
        }
        viewModel.onPracticeReminderPermissionResult(granted = granted, blocked = blocked)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AppearanceSection(
            viewModel = viewModel,
            onAlwaysUseDarkThemeChange = onAlwaysUseDarkThemeChange
        )
        PracticeRemindersSection(
            viewModel = viewModel,
            context = context,
            onRequestReminderPermission = {
                reminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
        MetronomePlaybackSection(
            viewModel = viewModel,
            metronomeViewModel = metronomeViewModel,
            onKeepScreenAwakeChange = onKeepScreenAwakeChange,
        )
        MetronomeInstrumentsSection(
            viewModel = viewModel,
            metronomeViewModel = metronomeViewModel
        )
        PlaylistInstrumentsSection(viewModel = viewModel)
        PolyrhythmInstrumentsSection(viewModel = viewModel)
        AboutSection()
    }

    viewModel.flashlightDialog?.let { dialog ->
        FlashlightSettingsDialogContent(
            dialog = dialog,
            onDismiss = { viewModel.dismissFlashlightDialog() }
        )
    }

    if (viewModel.showCrossDeviceReminderPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.declineRemindersFromOtherDevice() },
            title = { Text(stringResource(R.string.settings_practice_reminders)) },
            text = { Text(stringResource(R.string.settings_practice_reminders_cross_device_prompt)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (viewModel.allowRemindersFromOtherDevice(context.reminderPermissionStatus(viewModel))) {
                            ReminderSettingsAction.None -> Unit
                            ReminderSettingsAction.RequestPermission ->
                                reminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text(stringResource(R.string.allow_notifications))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.declineRemindersFromOtherDevice() }) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    }

    viewModel.reminderDialog?.let { dialog ->
        ReminderSettingsDialogContent(
            dialog = dialog,
            onDismiss = { viewModel.dismissReminderDialog() },
            onOpenSettings = {
                viewModel.dismissReminderDialog()
                context.openNotificationSettings()
            }
        )
    }
}

@Composable
private fun AppearanceSection(
    viewModel: SettingsViewModel,
    onAlwaysUseDarkThemeChange: (Boolean) -> Unit
) {
    SettingsSectionTitle(stringResource(R.string.appearance))
    SectionCard {
        SettingsToggleRow(
            label = stringResource(R.string.settings_always_use_dark_theme),
            checked = viewModel.alwaysUseDarkTheme,
            switchModifier = Modifier.testTag("always_use_dark_theme_switch"),
            onCheckedChange = {
                viewModel.updateAlwaysUseDarkTheme(it)
                onAlwaysUseDarkThemeChange(it)
            }
        )
    }
}

@Composable
private fun PracticeRemindersSection(
    viewModel: SettingsViewModel,
    context: Context,
    onRequestReminderPermission: () -> Unit
) {
    SettingsSectionTitle(stringResource(R.string.settings_practice_reminders))
    SectionCard {
        SettingsToggleRow(
            label = stringResource(R.string.settings_practice_reminders_label),
            checked = viewModel.practiceReminderEnabled,
            onCheckedChange = { enabled ->
                when (
                    viewModel.onPracticeReminderToggleRequested(
                        enabled = enabled,
                        status = context.reminderPermissionStatus(viewModel)
                    )
                ) {
                    ReminderSettingsAction.None -> Unit
                    ReminderSettingsAction.RequestPermission -> onRequestReminderPermission()
                }
            }
        )
        if (viewModel.practiceReminderEnabled) {
            SettingsDivider()
            SettingsValueRow(
                label = stringResource(R.string.settings_practice_reminder_time),
                value = formatReminderTime(
                    context = context,
                    hour = viewModel.practiceReminderHour,
                    minute = viewModel.practiceReminderMinute
                ),
                modifier = Modifier.clickable {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            viewModel.updatePracticeReminderTime(hour, minute)
                        },
                        viewModel.practiceReminderHour,
                        viewModel.practiceReminderMinute,
                        DateFormat.is24HourFormat(context)
                    ).show()
                }
            )
        }
    }
    SettingsFooter(stringResource(R.string.settings_practice_reminders_description))
    if (viewModel.practiceReminderEnabled && viewModel.notificationsBlockedLocally) {
        ReminderWarningRow(
            icon = Icons.Default.Warning,
            text = stringResource(R.string.settings_practice_reminders_blocked),
            actionText = stringResource(R.string.open_settings),
            onAction = { context.openNotificationSettings() }
        )
    } else if (viewModel.practiceReminderEnabled && viewModel.notificationsDeferredLocally) {
        ReminderWarningRow(
            icon = Icons.Default.NotificationsOff,
            text = stringResource(R.string.settings_practice_reminders_deferred),
            actionText = stringResource(R.string.enable),
            onAction = {
                when (viewModel.allowRemindersFromOtherDevice(context.reminderPermissionStatus(viewModel))) {
                    ReminderSettingsAction.None -> Unit
                    ReminderSettingsAction.RequestPermission -> onRequestReminderPermission()
                }
            }
        )
    }
    if (viewModel.practiceReminderEnabled && viewModel.exactAlarmsUnavailable) {
        ReminderWarningRow(
            icon = Icons.Default.Warning,
            text = stringResource(R.string.settings_practice_reminders_inexact_alarms),
            actionText = stringResource(R.string.open_settings),
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    } catch (_: android.content.ActivityNotFoundException) {
                        context.openNotificationSettings()
                    }
                }
            }
        )
    }
}

@Composable
private fun MetronomePlaybackSection(
    viewModel: SettingsViewModel,
    metronomeViewModel: MetronomeViewModel,
    onKeepScreenAwakeChange: (Boolean) -> Unit
) {
    SettingsSectionTitle(stringResource(R.string.settings_metronome_playback))
    SectionCard {
        SettingsToggleRow(
            label = stringResource(R.string.settings_flashlight),
            checked = viewModel.useFlashlight,
            onCheckedChange = { enabled ->
                viewModel.onFlashlightToggleRequested(enabled)
                metronomeViewModel.refreshPlaybackSettings()
            }
        )
        SettingsDivider()
        SettingsToggleRow(
            label = stringResource(R.string.settings_vibration),
            checked = viewModel.useVibration,
            onCheckedChange = {
                viewModel.updateUseVibration(it)
                metronomeViewModel.refreshPlaybackSettings()
            }
        )
        SettingsDivider()
        SettingsToggleRow(
            label = stringResource(R.string.settings_mute_metronome),
            checked = viewModel.muteMetronome,
            onCheckedChange = {
                viewModel.updateMuteMetronome(it)
                metronomeViewModel.refreshPlaybackSettings()
            }
        )
        SettingsDivider()
        SettingsToggleRow(
            label = stringResource(R.string.settings_keep_awake),
            checked = viewModel.keepScreenAwake,
            onCheckedChange = {
                viewModel.updateKeepScreenAwake(it)
                onKeepScreenAwakeChange(it)
            }
        )
        SettingsDivider()
        SettingsToggleRow(
            label = stringResource(R.string.settings_sixteenth_alternate),
            checked = viewModel.sixteenthAlternate,
            onCheckedChange = {
                viewModel.updateSixteenthAlternate(it)
                metronomeViewModel.refreshPlaybackSettings()
            }
        )
    }
    SettingsFooter(stringResource(R.string.settings_metronome_playback_description))
}

@Composable
private fun MetronomeInstrumentsSection(
    viewModel: SettingsViewModel,
    metronomeViewModel: MetronomeViewModel
) {
    SettingsSectionTitle(stringResource(R.string.settings_metronome_instruments))
    SectionCard {
        SoundPickerRow(
            label = stringResource(R.string.beat),
            selected = viewModel.metronomeBeatSound,
            options = SoundFile.beatSounds,
            onSelect = {
                viewModel.updateMetronomeBeatSound(it)
                metronomeViewModel.applyMetronomeSoundSettings(it, viewModel.metronomeRhythmSound)
            }
        )
        SettingsDivider()
        SoundPickerRow(
            label = stringResource(R.string.rhythm),
            selected = viewModel.metronomeRhythmSound,
            options = SoundFile.rhythmSounds,
            onSelect = {
                viewModel.updateMetronomeRhythmSound(it)
                metronomeViewModel.applyMetronomeSoundSettings(viewModel.metronomeBeatSound, it)
            }
        )
    }
}

@Composable
private fun PlaylistInstrumentsSection(viewModel: SettingsViewModel) {
    SettingsSectionTitle(stringResource(R.string.settings_playlist_instruments))
    SectionCard {
        SoundPickerRow(
            label = stringResource(R.string.beat),
            selected = viewModel.playlistBeatSound,
            options = SoundFile.beatSounds,
            onSelect = { viewModel.updatePlaylistBeatSound(it) }
        )
        SettingsDivider()
        SoundPickerRow(
            label = stringResource(R.string.rhythm),
            selected = viewModel.playlistRhythmSound,
            options = SoundFile.rhythmSounds,
            onSelect = { viewModel.updatePlaylistRhythmSound(it) }
        )
    }
    SettingsFooter(stringResource(R.string.settings_instruments_description))
}

@Composable
private fun PolyrhythmInstrumentsSection(viewModel: SettingsViewModel) {
    SettingsSectionTitle(stringResource(R.string.settings_polyrhythm_instruments))
    SectionCard {
        SoundPickerRow(
            label = stringResource(R.string.beat),
            selected = viewModel.polyrhythmBeatSound,
            options = SoundFile.beatSounds,
            onSelect = { viewModel.updatePolyrhythmBeatSound(it) }
        )
        SettingsDivider()
        SoundPickerRow(
            label = stringResource(R.string.rhythm),
            selected = viewModel.polyrhythmRhythmSound,
            options = SoundFile.rhythmSounds,
            onSelect = { viewModel.updatePolyrhythmRhythmSound(it) }
        )
    }
}

@Composable
private fun AboutSection() {
    SettingsSectionTitle(stringResource(R.string.about))
    SectionCard {
        SettingsValueRow(
            label = stringResource(R.string.version),
            value = stringResource(R.string.version_value)
        )
        SettingsDivider()
        SettingsValueRow(
            label = stringResource(R.string.copyright),
            value = stringResource(R.string.copyright_value)
        )
    }
    Spacer(modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun FlashlightSettingsDialogContent(
    dialog: FlashlightSettingsDialog,
    onDismiss: () -> Unit
) {
    when (dialog) {
        FlashlightSettingsDialog.Unavailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.settings_flashlight_unavailable_title)) },
                text = { Text(stringResource(R.string.settings_flashlight_unavailable)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
    }
}

@Composable
private fun ReminderSettingsDialogContent(
    dialog: ReminderSettingsDialog,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    when (dialog) {
        is ReminderSettingsDialog.PermissionDenied -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.settings_practice_reminders_notifications_disabled_title)) },
                text = { Text(stringResource(R.string.settings_practice_reminders_notifications_disabled)) },
                confirmButton = {
                    if (dialog.blocked) {
                        TextButton(onClick = onOpenSettings) {
                            Text(stringResource(R.string.open_settings))
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                },
                dismissButton = {
                    if (dialog.blocked) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ReminderWarningRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        )
        TextButton(onClick = onAction) {
            Text(actionText)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @SuppressLint("ModifierParameter") switchModifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier
        )
    }
}

@Composable
private fun SettingsValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 12.dp))
}

private fun formatReminderTime(context: android.content.Context, hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.reminderPermissionStatus(
    viewModel: SettingsViewModel,
    permissionRequestedOverride: Boolean? = null
): ReminderPermissionStatus {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return ReminderPermissionStatus.Granted
    }
    if (
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return ReminderPermissionStatus.Granted
    }

    val permissionRequested = permissionRequestedOverride
        ?: viewModel.practiceReminderNotificationPermissionRequested
    if (!permissionRequested) return ReminderPermissionStatus.NotDetermined

    val canAskAgain = findActivity()?.let { activity ->
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } ?: false
    return if (canAskAgain) ReminderPermissionStatus.Denied else ReminderPermissionStatus.Blocked
}

private fun Context.openNotificationSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        )
    } else {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}
