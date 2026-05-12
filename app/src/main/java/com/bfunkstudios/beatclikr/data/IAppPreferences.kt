package com.bfunkstudios.beatclikr.data

interface IAppPreferences {
    var instantBpm: Float
    var instantGroove: Groove
    var instantBeatPattern: BeatPattern?
    var rampEnabled: Boolean
    var rampIncrement: Int
    var rampInterval: Int
    var instantBeatSound: SoundFile
    var instantRhythmSound: SoundFile
    var playlistBeatSound: SoundFile
    var playlistRhythmSound: SoundFile
    var polyrhythmBpm: Float
    var polyrhythmBeats: Int
    var polyrhythmAgainst: Int
    var polyrhythmBeatSound: SoundFile
    var polyrhythmRhythmSound: SoundFile
    var useVibration: Boolean
    var useFlashlight: Boolean
    var alwaysUseDarkTheme: Boolean
    var muteMetronome: Boolean
    var keepScreenAwake: Boolean
    var sixteenthAlternate: Boolean
    var practiceReminderEnabled: Boolean
    var practiceReminderHour: Int
    var practiceReminderMinute: Int
    var practiceReminderNotificationsDeferred: Boolean
    var practiceReminderNotificationPermissionRequested: Boolean
}
