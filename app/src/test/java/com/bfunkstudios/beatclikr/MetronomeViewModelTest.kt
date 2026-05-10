package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.BeatPattern
import com.bfunkstudios.beatclikr.data.ClickerType
import com.bfunkstudios.beatclikr.data.Groove
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.ui.MetronomeViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeViewModelTest {

    private lateinit var audio: IAudioPlayerService
    private lateinit var prefs: IAppPreferences
    private lateinit var viewModel: MetronomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        audio = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        every { prefs.instantBpm } returns 120f
        every { prefs.instantGroove } returns Groove.Quarter
        every { prefs.instantBeatPattern } returns null
        every { prefs.instantBeatSound } returns SoundFile.CLICK_HI
        every { prefs.instantRhythmSound } returns SoundFile.CLICK_LO
        every { prefs.rampEnabled } returns false
        every { prefs.rampIncrement } returns 2
        every { prefs.rampInterval } returns 8
        every { prefs.muteMetronome } returns false
        viewModel = MetronomeViewModel(audio, prefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Initialization ---

    @Test
    fun `initial BPM loaded from prefs`() {
        assertEquals(120f, viewModel.beatsPerMinute)
    }

    @Test
    fun `initial subdivisions loaded from prefs`() {
        assertEquals(Groove.Quarter, viewModel.selectedGroove)
    }

    @Test
    fun `initial beat sound loaded from prefs`() {
        assertEquals(SoundFile.CLICK_HI, viewModel.selectedBeatSound)
    }

    @Test
    fun `initial rhythm sound loaded from prefs`() {
        assertEquals(SoundFile.CLICK_LO, viewModel.selectedRhythmSound)
    }

    @Test
    fun `initial state is not playing`() {
        assertFalse(viewModel.isPlaying)
    }

    @Test
    fun `init sets audio delegate to viewModel`() {
        verify { audio.delegate = viewModel }
    }

    // --- BPM ---

    @Test
    fun `updateBPM clamps to min`() {
        viewModel.updateBPM(0f)
        assertEquals(MetronomeConstants.MIN_BPM, viewModel.beatsPerMinute)
    }

    @Test
    fun `updateBPM clamps to max`() {
        viewModel.updateBPM(9999f)
        assertEquals(MetronomeConstants.MAX_BPM, viewModel.beatsPerMinute)
    }

    @Test
    fun `updateBPM sets valid value`() {
        viewModel.updateBPM(140f)
        assertEquals(140f, viewModel.beatsPerMinute)
    }

    @Test
    fun `updateBPM while playing calls updateTempo`() {
        viewModel.start()
        viewModel.updateBPM(150f)
        verify { audio.updateTempo(150f, any()) }
    }

    @Test
    fun `updateBPM while stopped does not call updateTempo`() {
        viewModel.updateBPM(150f)
        verify(exactly = 0) { audio.updateTempo(any(), any()) }
    }

    @Test
    fun `updateBPM in instant mode saves to prefs`() {
        viewModel.updateBPM(140f)
        verify { prefs.instantBpm = 140f }
    }

    // --- Tempo ramp ---

    @Test
    fun `ramp defaults are loaded from prefs`() {
        assertFalse(viewModel.rampEnabled)
        assertEquals(2, viewModel.rampIncrement)
        assertEquals(8, viewModel.rampInterval)
    }

    @Test
    fun `updateRampEnabled saves to prefs`() {
        viewModel.updateRampEnabled(true)
        assertTrue(viewModel.rampEnabled)
        verify { prefs.rampEnabled = true }
    }

    @Test
    fun `updateRampIncrement saves to prefs`() {
        viewModel.updateRampIncrement(5)
        assertEquals(5, viewModel.rampIncrement)
        verify { prefs.rampIncrement = 5 }
    }

    @Test
    fun `updateRampInterval saves to prefs`() {
        viewModel.updateRampInterval(16)
        assertEquals(16, viewModel.rampInterval)
        verify { prefs.rampInterval = 16 }
    }

    @Test
    fun `ramp does not fire when disabled`() {
        viewModel.updateBPM(100f)
        viewModel.updateRampEnabled(false)
        viewModel.updateRampInterval(4)
        viewModel.updateRampIncrement(5)
        repeat(10) { viewModel.metronomeBeatFired(isBeat = true, beatInterval = 0.5f) }
        assertEquals(100f, viewModel.beatsPerMinute)
    }

    @Test
    fun `ramp only counts beats not subdivisions`() {
        viewModel.updateBPM(100f)
        viewModel.updateRampEnabled(true)
        viewModel.updateRampInterval(4)
        viewModel.updateRampIncrement(5)
        repeat(20) { viewModel.metronomeBeatFired(isBeat = false, beatInterval = 0.5f) }
        assertEquals(100f, viewModel.beatsPerMinute)
    }

    @Test
    fun `ramp fires after interval beats`() {
        viewModel.updateBPM(100f)
        viewModel.updateRampEnabled(true)
        viewModel.updateRampInterval(4)
        viewModel.updateRampIncrement(5)
        repeat(5) { viewModel.metronomeBeatFired(isBeat = true, beatInterval = 0.5f) }
        assertEquals(105f, viewModel.beatsPerMinute)
    }

    @Test
    fun `ramp caps at max BPM`() {
        viewModel.updateBPM(MetronomeConstants.MAX_BPM - 5f)
        viewModel.updateRampEnabled(true)
        viewModel.updateRampInterval(4)
        viewModel.updateRampIncrement(10)
        repeat(5) { viewModel.metronomeBeatFired(isBeat = true, beatInterval = 0.5f) }
        assertEquals(MetronomeConstants.MAX_BPM, viewModel.beatsPerMinute)
    }

    @Test
    fun `stop restores starting BPM when ramp enabled`() {
        viewModel.updateBPM(100f)
        viewModel.updateRampEnabled(true)
        viewModel.updateRampInterval(4)
        viewModel.updateRampIncrement(5)
        viewModel.start()
        repeat(5) { viewModel.metronomeBeatFired(isBeat = true, beatInterval = 0.5f) }
        assertEquals(105f, viewModel.beatsPerMinute)
        viewModel.stop()
        assertEquals(100f, viewModel.beatsPerMinute)
    }

    // --- Grooves ---

    @Test
    fun `updateGroove updates current song`() {
        viewModel.updateGroove(Groove.Eighth)
        assertEquals(Groove.Eighth, viewModel.selectedGroove)
    }

    @Test
    fun `updateGroove while playing calls updateTempo`() {
        viewModel.start()
        viewModel.updateGroove(Groove.Sixteenth)
        verify { audio.updateTempo(any(), 4) }
    }

    @Test
    fun `updateGroove while stopped does not call updateTempo`() {
        viewModel.updateGroove(Groove.Eighth)
        verify(exactly = 0) { audio.updateTempo(any(), any()) }
    }

    @Test
    fun `updateGroove in instant mode saves to prefs`() {
        viewModel.updateGroove(Groove.Triplet)
        verify { prefs.instantGroove = Groove.Triplet }
    }

    @Test
    fun `odd meter grooves expose iOS subdivision values`() {
        viewModel.updateGroove(Groove.OddMeterQuarter)
        viewModel.start()
        verify { audio.startMetronome(any(), 1, BeatPattern.default.accentArray) }

        viewModel.updateGroove(Groove.OddMeterEighth)
        verify { audio.updateTempo(any(), 2, BeatPattern.default.accentArray) }
    }

    @Test
    fun `updateBeatPattern saves to prefs and updates tempo while playing`() {
        viewModel.updateGroove(Groove.OddMeterEighth)
        viewModel.start()
        viewModel.updateBeatPattern(BeatPattern.FiveEightA)
        assertEquals(BeatPattern.FiveEightA, viewModel.selectedBeatPattern)
        verify { prefs.instantBeatPattern = BeatPattern.FiveEightA }
        verify { audio.updateTempo(any(), 2, BeatPattern.FiveEightA.accentArray) }
    }

    // --- Play / Stop ---

    @Test
    fun `togglePlayPause starts playback`() {
        viewModel.togglePlayPause()
        assertTrue(viewModel.isPlaying)
    }

    @Test
    fun `togglePlayPause stops playback when already playing`() {
        viewModel.togglePlayPause()
        viewModel.togglePlayPause()
        assertFalse(viewModel.isPlaying)
    }

    @Test
    fun `start calls audio startMetronome`() {
        viewModel.start()
        verify { audio.startMetronome(any(), any()) }
    }

    @Test
    fun `stop calls audio stopMetronome`() {
        viewModel.start()
        viewModel.stop()
        verify { audio.stopMetronome() }
        assertFalse(viewModel.isPlaying)
    }

    // --- Mute ---

    @Test
    fun `start propagates mute false to audio`() {
        every { prefs.muteMetronome } returns false
        viewModel.start()
        verifyOrder {
            audio.isMuted = false
            audio.startMetronome(any(), any())
        }
    }

    @Test
    fun `start propagates mute true to audio`() {
        every { prefs.muteMetronome } returns true
        viewModel.start()
        verifyOrder {
            audio.isMuted = true
            audio.startMetronome(any(), any())
        }
    }

    // --- Sound selection ---

    @Test
    fun `updateBeatSound updates selectedBeatSound`() {
        viewModel.updateBeatSound(SoundFile.KICK)
        assertEquals(SoundFile.KICK, viewModel.selectedBeatSound)
    }

    @Test
    fun `updateRhythmSound updates selectedRhythmSound`() {
        viewModel.updateRhythmSound(SoundFile.SNARE)
        assertEquals(SoundFile.SNARE, viewModel.selectedRhythmSound)
    }

    @Test
    fun `updateBeatSound in instant mode saves to prefs`() {
        viewModel.updateBeatSound(SoundFile.KICK)
        verify { prefs.instantBeatSound = SoundFile.KICK }
    }

    @Test
    fun `updateRhythmSound in instant mode saves to prefs`() {
        viewModel.updateRhythmSound(SoundFile.SNARE)
        verify { prefs.instantRhythmSound = SoundFile.SNARE }
    }

    // --- loadSong ---

    @Test
    fun `loadSong updates currentSong`() {
        val song = Song(title = "Test", artist = "Artist", beatsPerMinute = 140f, beatsPerMeasure = 4, groove = Groove.Eighth, liveSequence = null, rehearsalSequence = null)
        viewModel.loadSong(song, ClickerType.INSTANT)
        assertEquals(140f, viewModel.beatsPerMinute)
        assertEquals(Groove.Eighth, viewModel.selectedGroove)
    }

    @Test
    fun `loadSong while playing calls updateTempo`() {
        viewModel.start()
        val song = Song(title = "Test", artist = "Artist", beatsPerMinute = 140f, beatsPerMeasure = 4, groove = Groove.Eighth, liveSequence = null, rehearsalSequence = null)
        viewModel.loadSong(song, ClickerType.INSTANT)
        verify { audio.updateTempo(140f, 2) }
    }

    // --- Beat fired ---

    @Test
    fun `metronomeBeatFired on beat sets iconScale to max`() {
        viewModel.metronomeBeatFired(isBeat = true, beatInterval = 0.5f)
        assertEquals(MetronomeConstants.ICON_SCALE_MAX, viewModel.iconScale)
    }

    @Test
    fun `metronomeBeatFired on beat starts beat pulse`() {
        viewModel.metronomeBeatFired(isBeat = true, beatInterval = 0.5f)
        assertEquals(1f, viewModel.beatPulse)
    }

    @Test
    fun `metronomeBeatFired on subdivision does not set iconScale to max`() {
        viewModel.metronomeBeatFired(isBeat = false, beatInterval = 0.5f)
        assertEquals(MetronomeConstants.ICON_SCALE_MIN, viewModel.iconScale)
    }

    @Test
    fun `stop resets beat pulse`() {
        viewModel.metronomeBeatFired(isBeat = true, beatInterval = 0.5f)
        viewModel.stop()
        assertEquals(0f, viewModel.beatPulse)
    }

    // --- Tap tempo ---

    @Test
    fun `recordTap with single tap does not change BPM`() {
        val initialBpm = viewModel.beatsPerMinute
        viewModel.recordTap()
        assertEquals(initialBpm, viewModel.beatsPerMinute)
    }

    @Test
    fun `recordTap with two rapid taps clamps to max BPM`() {
        viewModel.recordTap()
        viewModel.recordTap()
        assertEquals(MetronomeConstants.MAX_BPM, viewModel.beatsPerMinute)
    }
}
