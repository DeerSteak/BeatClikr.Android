package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.ui.PolyrhythmViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
class PolyrhythmViewModelTest {

    private lateinit var audio: IAudioPlayerService
    private lateinit var prefs: IAppPreferences
    private lateinit var viewModel: PolyrhythmViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        audio = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        every { prefs.polyrhythmBpm } returns 120f
        every { prefs.polyrhythmBeats } returns 3
        every { prefs.polyrhythmAgainst } returns 2
        every { prefs.polyrhythmBeatSound } returns SoundFile.CLICK_HI
        every { prefs.polyrhythmRhythmSound } returns SoundFile.CLICK_LO
        every { prefs.muteMetronome } returns false
        viewModel = PolyrhythmViewModel(audio, prefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads from prefs`() {
        assertEquals(120f, viewModel.bpm)
        assertEquals(3, viewModel.beats)
        assertEquals(2, viewModel.against)
        assertFalse(viewModel.isPlaying)
    }

    @Test
    fun `init sets polyrhythm delegate`() {
        verify { audio.polyrhythmDelegate = viewModel }
    }

    @Test
    fun `start begins polyrhythm playback`() {
        viewModel.start()
        assertTrue(viewModel.isPlaying)
        verify { audio.isMuted = false }
        verify { audio.startPolyrhythm(120f, 3, 2) }
    }

    @Test
    fun `stop ends polyrhythm playback`() {
        viewModel.start()
        viewModel.stop()
        assertFalse(viewModel.isPlaying)
        verify { audio.stopPolyrhythm() }
    }

    @Test
    fun `changing counts while playing restarts playback`() {
        viewModel.start()
        val resetId = viewModel.playheadResetID
        viewModel.updateBeats(4)
        assertEquals(4, viewModel.beats)
        assertTrue(viewModel.playheadResetID > resetId)
        verify { prefs.polyrhythmBeats = 4 }
        verify { audio.startPolyrhythm(120f, 4, 2) }
    }

    @Test
    fun `delegate updates active indexes`() {
        viewModel.polyrhythmBeatFired(
            beatFired = true,
            rhythmFired = true,
            beatIndex = 1,
            rhythmIndex = 2
        )
        assertEquals(1, viewModel.activeBeatIndex)
        assertEquals(2, viewModel.activeRhythmIndex)
        assertTrue(viewModel.beatPulse > 0f)
        assertTrue(viewModel.rhythmPulse > 0f)
    }

    @Test
    fun `cycle duration follows iOS formula`() {
        viewModel.updateBpm(120f)
        viewModel.updateAgainst(4)
        assertEquals(2000, viewModel.cycleDurationMillis)
    }
}
