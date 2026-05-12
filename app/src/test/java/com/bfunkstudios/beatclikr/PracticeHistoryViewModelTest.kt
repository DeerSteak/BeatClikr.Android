package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.PracticeHistoryRepository
import com.bfunkstudios.beatclikr.ui.PracticeHistoryViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class PracticeHistoryViewModelTest {

    private lateinit var repository: PracticeHistoryRepository
    private lateinit var viewModel: PracticeHistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk(relaxed = true)
        every { repository.getAllSessions() } returns flowOf(emptyList())
        viewModel = PracticeHistoryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `streakStats returns empty defaults for no practice dates`() {
        val stats = viewModel.streakStats(emptySet())

        assertEquals(0, stats.currentValue)
        assertEquals("0 days", stats.currentValueLabel)
        assertEquals(0, stats.longestValue)
        assertEquals("0 days", stats.longestValueLabel)
        assertFalse(stats.reminderNeeded)
        assertEquals("0", stats.shareCardStreakDays)
    }

    @Test
    fun `streakStats reports current streak through yesterday and reminder needed`() {
        val yesterday = PracticeHistoryViewModel.startOfDay(
            System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        )

        val stats = viewModel.streakStats(setOf(yesterday))

        assertEquals(1, stats.currentValue)
        assertEquals("1 day", stats.currentValueLabel)
        assertEquals(1, stats.longestValue)
        assertEquals("1 day", stats.longestValueLabel)
        assertTrue(stats.reminderNeeded)
        assertEquals("1", stats.shareCardStreakDays)
    }
}
