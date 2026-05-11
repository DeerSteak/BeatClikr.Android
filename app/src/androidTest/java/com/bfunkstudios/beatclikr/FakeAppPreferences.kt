package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.Groove
import com.bfunkstudios.beatclikr.data.BeatPattern
import com.bfunkstudios.beatclikr.data.SoundFile

class FakeAppPreferences : IAppPreferences {
    init {
        instance = this
    }

    override var instantBpm: Float = 60f
    override var instantGroove: Groove = Groove.Quarter
    override var instantBeatPattern: BeatPattern? = null
    override var rampEnabled: Boolean = false
    override var rampIncrement: Int = 2
    override var rampInterval: Int = 8
    override var instantBeatSound: SoundFile = SoundFile.CLICK_HI
    override var instantRhythmSound: SoundFile = SoundFile.CLICK_LO
    override var playlistBeatSound: SoundFile = SoundFile.CLICK_HI
    override var playlistRhythmSound: SoundFile = SoundFile.CLICK_LO
    override var polyrhythmBpm: Float = 60f
    override var polyrhythmBeats: Int = 3
    override var polyrhythmAgainst: Int = 2
    override var polyrhythmBeatSound: SoundFile = SoundFile.CLICK_HI
    override var polyrhythmRhythmSound: SoundFile = SoundFile.CLICK_LO
    override var useVibration: Boolean = false
    override var useFlashlight: Boolean = false
    override var alwaysUseDarkTheme: Boolean = true
    override var muteMetronome: Boolean = false
    override var keepScreenAwake: Boolean = false
    override var sixteenthAlternate: Boolean = false
    override var practiceReminderEnabled: Boolean = false
    override var practiceReminderHour: Int = 9
    override var practiceReminderMinute: Int = 0
    override var practiceReminderNotificationsDeferred: Boolean = false
    override var practiceReminderNotificationPermissionRequested: Boolean = false

    companion object {
        lateinit var instance: FakeAppPreferences
    }
}
