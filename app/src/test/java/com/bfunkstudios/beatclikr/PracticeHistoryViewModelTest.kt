package com.bfunkstudios.beatclikr

import android.app.Application
import android.content.res.Resources
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

    private lateinit var application: Application
    private lateinit var repository: PracticeHistoryRepository
    private lateinit var viewModel: PracticeHistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val resources = mockk<Resources>()
        application = mockk()
        every { application.resources } returns resources
        every { application.getString(any()) } returns "Let's go"
        every { application.getString(any(), *anyVararg()) } returns "Streak"
        every { resources.getQuantityString(R.plurals.day_count, any(), any()) } answers {
            val count = secondArg<Int>()
            if (count == 1) "1 day" else "$count days"
        }
        repository = mockk(relaxed = true)
        every { repository.getAllSessions() } returns flowOf(emptyList())
        viewModel = PracticeHistoryViewModel(application, repository)
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
