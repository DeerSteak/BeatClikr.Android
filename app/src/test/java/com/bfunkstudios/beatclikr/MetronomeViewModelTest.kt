package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.ClickerType
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.data.Subdivisions
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.ui.MetronomeViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
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
        every { prefs.instantSubdivisions } returns Subdivisions.Quarter
        every { prefs.instantBeatSound } returns SoundFile.CLICK_HI
        every { prefs.instantRhythmSound } returns SoundFile.CLICK_LO
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
        assertEquals(Subdivisions.Quarter, viewModel.selectedSubdivisions)
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

    // --- Subdivisions ---

    @Test
    fun `updateSubdivisions updates current song`() {
        viewModel.updateSubdivisions(Subdivisions.Eighth)
        assertEquals(Subdivisions.Eighth, viewModel.selectedSubdivisions)
    }

    @Test
    fun `updateSubdivisions while playing calls updateTempo`() {
        viewModel.start()
        viewModel.updateSubdivisions(Subdivisions.Sixteenth)
        verify { audio.updateTempo(any(), 4) }
    }

    @Test
    fun `updateSubdivisions while stopped does not call updateTempo`() {
        viewModel.updateSubdivisions(Subdivisions.Eighth)
        verify(exactly = 0) { audio.updateTempo(any(), any()) }
    }

    @Test
    fun `updateSubdivisions in instant mode saves to prefs`() {
        viewModel.updateSubdivisions(Subdivisions.Triplet)
        verify { prefs.instantSubdivisions = Subdivisions.Triplet }
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
        verifySequence {
            audio.isMuted = false
            audio.startMetronome(any(), any())
        }
    }

    @Test
    fun `start propagates mute true to audio`() {
        every { prefs.muteMetronome } returns true
        viewModel.start()
        verifySequence {
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
        val song = Song("Test", "Artist", 140f, 4, Subdivisions.Eighth, null, null)
        viewModel.loadSong(song, ClickerType.INSTANT)
        assertEquals(140f, viewModel.beatsPerMinute)
        assertEquals(Subdivisions.Eighth, viewModel.selectedSubdivisions)
    }

    @Test
    fun `loadSong while playing calls updateTempo`() {
        viewModel.start()
        val song = Song("Test", "Artist", 140f, 4, Subdivisions.Eighth, null, null)
        viewModel.loadSong(song, ClickerType.INSTANT)
        verify { audio.updateTempo(140f, 2) }
    }

    // --- Beat fired ---

    @Test
    fun `metronomeBeatFired on beat sets iconScale to max`() {
        viewModel.metronomeBeatFired(isBeat = true)
        assertEquals(MetronomeConstants.ICON_SCALE_MAX, viewModel.iconScale)
    }

    @Test
    fun `metronomeBeatFired on subdivision does not set iconScale to max`() {
        viewModel.metronomeBeatFired(isBeat = false)
        assertEquals(MetronomeConstants.ICON_SCALE_MIN, viewModel.iconScale)
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
