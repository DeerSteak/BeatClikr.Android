package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.data.Subdivisions

class FakeAppPreferences : IAppPreferences {
    override var instantBpm: Float = 120f
    override var instantSubdivisions: Subdivisions = Subdivisions.Quarter
    override var instantBeatSound: SoundFile = SoundFile.CLICK_HI
    override var instantRhythmSound: SoundFile = SoundFile.CLICK_LO
    override var playlistBeatSound: SoundFile = SoundFile.CLICK_HI
    override var playlistRhythmSound: SoundFile = SoundFile.CLICK_LO
    override var useVibration: Boolean = false
    override var useFlashlight: Boolean = false
    override var muteMetronome: Boolean = false
    override var keepScreenAwake: Boolean = false
    override var practiceReminderEnabled: Boolean = false
    override var practiceReminderHour: Int = 9
    override var practiceReminderMinute: Int = 0
}
