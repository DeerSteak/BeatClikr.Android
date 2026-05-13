package com.bfunkstudios.beatclikr.data

import android.content.Context
import androidx.core.content.edit

class AppPreferences(context: Context) : IAppPreferences {

    private val prefs = context.getSharedPreferences("beatclikr_preferences", Context.MODE_PRIVATE)

    // --- Instant mode ---

    override var instantBpm: Float
        get() = prefs.getFloat(Keys.INSTANT_BPM, DEFAULT_BPM)
        set(value) = prefs.edit { putFloat(Keys.INSTANT_BPM, value) }

    override var instantGroove: Groove
        get() = Groove.valueOf(
            prefs.getString(Keys.INSTANT_SUBDIVISIONS, Groove.Quarter.name)!!
        )
        set(value) = prefs.edit { putString(Keys.INSTANT_SUBDIVISIONS, value.name) }

    override var instantBeatPattern: BeatPattern?
        get() = prefs.getString(Keys.INSTANT_BEAT_PATTERN, null)?.let { BeatPattern.fromRawValue(it) }
        set(value) = prefs.edit { putString(Keys.INSTANT_BEAT_PATTERN, value?.rawValue) }

    override var rampEnabled: Boolean
        get() = prefs.getBoolean(Keys.RAMP_ENABLED, false)
        set(value) = prefs.edit { putBoolean(Keys.RAMP_ENABLED, value) }

    override var rampIncrement: Int
        get() = prefs.getInt(Keys.RAMP_INCREMENT, 2).takeIf { it > 0 } ?: 2
        set(value) = prefs.edit { putInt(Keys.RAMP_INCREMENT, value) }

    override var rampInterval: Int
        get() = prefs.getInt(Keys.RAMP_INTERVAL, 8).takeIf { it > 0 } ?: 8
        set(value) = prefs.edit { putInt(Keys.RAMP_INTERVAL, value) }

    override var instantBeatSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.INSTANT_BEAT_SOUND, SoundFile.CLICK_HI.name)!!
        )
        set(value) = prefs.edit { putString(Keys.INSTANT_BEAT_SOUND, value.name) }

    override var instantRhythmSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.INSTANT_RHYTHM_SOUND, SoundFile.CLICK_LO.name)!!
        )
        set(value) = prefs.edit { putString(Keys.INSTANT_RHYTHM_SOUND, value.name) }

    // --- Playlist mode ---

    override var playlistBeatSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.PLAYLIST_BEAT_SOUND, SoundFile.CLICK_HI.name)!!
        )
        set(value) = prefs.edit { putString(Keys.PLAYLIST_BEAT_SOUND, value.name) }

    override var playlistRhythmSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.PLAYLIST_RHYTHM_SOUND, SoundFile.CLICK_LO.name)!!
        )
        set(value) = prefs.edit { putString(Keys.PLAYLIST_RHYTHM_SOUND, value.name) }

    // --- Polyrhythm mode ---

    override var polyrhythmBpm: Float
        get() = prefs.getFloat(Keys.POLYRHYTHM_BPM, DEFAULT_BPM)
        set(value) = prefs.edit { putFloat(Keys.POLYRHYTHM_BPM, value) }

    override var polyrhythmBeats: Int
        get() = prefs.getInt(Keys.POLYRHYTHM_BEATS, 3).coerceIn(1, 15)
        set(value) = prefs.edit { putInt(Keys.POLYRHYTHM_BEATS, value) }

    override var polyrhythmAgainst: Int
        get() = prefs.getInt(Keys.POLYRHYTHM_AGAINST, 2).coerceIn(1, 15)
        set(value) = prefs.edit { putInt(Keys.POLYRHYTHM_AGAINST, value) }

    override var polyrhythmBeatSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.POLYRHYTHM_BEAT_SOUND, SoundFile.CLICK_HI.name)!!
        )
        set(value) = prefs.edit { putString(Keys.POLYRHYTHM_BEAT_SOUND, value.name) }

    override var polyrhythmRhythmSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.POLYRHYTHM_RHYTHM_SOUND, SoundFile.CLICK_LO.name)!!
        )
        set(value) = prefs.edit { putString(Keys.POLYRHYTHM_RHYTHM_SOUND, value.name) }

    // --- Behavior ---

    override var useVibration: Boolean
        get() = prefs.getBoolean(Keys.USE_VIBRATION, false)
        set(value) = prefs.edit { putBoolean(Keys.USE_VIBRATION, value) }

    override var useFlashlight: Boolean
        get() = prefs.getBoolean(Keys.USE_FLASHLIGHT, false)
        set(value) = prefs.edit { putBoolean(Keys.USE_FLASHLIGHT, value) }

    override var alwaysUseDarkTheme: Boolean
        get() = prefs.getBoolean(Keys.ALWAYS_USE_DARK_THEME, true)
        set(value) = prefs.edit { putBoolean(Keys.ALWAYS_USE_DARK_THEME, value) }

    override var muteMetronome: Boolean
        get() = prefs.getBoolean(Keys.MUTE_METRONOME, false)
        set(value) = prefs.edit { putBoolean(Keys.MUTE_METRONOME, value) }

    override var keepScreenAwake: Boolean
        get() = prefs.getBoolean(Keys.KEEP_SCREEN_AWAKE, false)
        set(value) = prefs.edit { putBoolean(Keys.KEEP_SCREEN_AWAKE, value) }

    override var sixteenthAlternate: Boolean
        get() = prefs.getBoolean(Keys.SIXTEENTH_ALTERNATE, false)
        set(value) = prefs.edit { putBoolean(Keys.SIXTEENTH_ALTERNATE, value) }

    override var useAudioTrack: Boolean
        get() = prefs.getBoolean(Keys.USE_AUDIO_TRACK, false)
        set(value) = prefs.edit { putBoolean(Keys.USE_AUDIO_TRACK, value) }

    override var useSyntheticAudioTrackSounds: Boolean
        get() = prefs.getBoolean(Keys.USE_SYNTHETIC_AUDIO_TRACK_SOUNDS, true)
        set(value) = prefs.edit { putBoolean(Keys.USE_SYNTHETIC_AUDIO_TRACK_SOUNDS, value) }

    // --- Practice reminders ---

    override var practiceReminderEnabled: Boolean
        get() = prefs.getBoolean(Keys.REMINDER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(Keys.REMINDER_ENABLED, value) }

    override var practiceReminderHour: Int
        get() = prefs.getInt(Keys.REMINDER_HOUR, 9)
        set(value) = prefs.edit { putInt(Keys.REMINDER_HOUR, value) }

    override var practiceReminderMinute: Int
        get() = prefs.getInt(Keys.REMINDER_MINUTE, 0)
        set(value) = prefs.edit { putInt(Keys.REMINDER_MINUTE, value) }

    override var practiceReminderNotificationsDeferred: Boolean
        get() = prefs.getBoolean(Keys.REMINDER_NOTIFICATIONS_DEFERRED, false)
        set(value) = prefs.edit { putBoolean(Keys.REMINDER_NOTIFICATIONS_DEFERRED, value) }

    override var practiceReminderNotificationPermissionRequested: Boolean
        get() = prefs.getBoolean(Keys.REMINDER_NOTIFICATION_PERMISSION_REQUESTED, false)
        set(value) = prefs.edit { putBoolean(Keys.REMINDER_NOTIFICATION_PERMISSION_REQUESTED, value) }

    private object Keys {
        const val INSTANT_BPM = "instant_bpm"
        const val INSTANT_SUBDIVISIONS = "instant_subdivisions"
        const val INSTANT_BEAT_PATTERN = "instant_beat_pattern"
        const val RAMP_ENABLED = "ramp_enabled"
        const val RAMP_INCREMENT = "ramp_increment"
        const val RAMP_INTERVAL = "ramp_interval"
        const val INSTANT_BEAT_SOUND = "instant_beat_sound"
        const val INSTANT_RHYTHM_SOUND = "instant_rhythm_sound"
        const val PLAYLIST_BEAT_SOUND = "playlist_beat_sound"
        const val PLAYLIST_RHYTHM_SOUND = "playlist_rhythm_sound"
        const val POLYRHYTHM_BPM = "polyrhythm_bpm"
        const val POLYRHYTHM_BEATS = "polyrhythm_beats"
        const val POLYRHYTHM_AGAINST = "polyrhythm_against"
        const val POLYRHYTHM_BEAT_SOUND = "polyrhythm_beat_sound"
        const val POLYRHYTHM_RHYTHM_SOUND = "polyrhythm_rhythm_sound"
        const val USE_VIBRATION = "use_vibration"
        const val USE_FLASHLIGHT = "use_flashlight"
        const val ALWAYS_USE_DARK_THEME = "always_use_dark_theme"
        const val MUTE_METRONOME = "mute_metronome"
        const val KEEP_SCREEN_AWAKE = "keep_screen_awake"
        const val SIXTEENTH_ALTERNATE = "sixteenth_alternate"
        const val USE_AUDIO_TRACK = "use_audio_track"
        const val USE_SYNTHETIC_AUDIO_TRACK_SOUNDS = "use_synthetic_audio_track_sounds"
        const val REMINDER_ENABLED = "reminder_enabled"
        const val REMINDER_HOUR = "reminder_hour"
        const val REMINDER_MINUTE = "reminder_minute"
        const val REMINDER_NOTIFICATIONS_DEFERRED = "reminder_notifications_deferred"
        const val REMINDER_NOTIFICATION_PERMISSION_REQUESTED = "reminder_notification_permission_requested"
    }

    private companion object {
        const val DEFAULT_BPM = 60f
    }
}
