package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.PracticeReminderBodyCalculator
import com.bfunkstudios.beatclikr.services.PracticeReminderBodySpec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class PracticeReminderBodyCalculatorTest {

    private val today = day(2026, Calendar.MAY, 11)

    @Test
    fun `notification body is default for empty dates`() {
        val body = PracticeReminderBodyCalculator.notificationBody(emptySet(), today)

        assertEquals(PracticeReminderBodySpec.Default, body)
    }

    @Test
    fun `notification body is practiced today when today is in dates`() {
        val body = PracticeReminderBodyCalculator.notificationBody(setOf(today), today)

        assertEquals(PracticeReminderBodySpec.PracticedToday, body)
    }

    @Test
    fun `notification body keeps streak when last practice was yesterday`() {
        val dates = setOf(daysAgo(1), daysAgo(2), daysAgo(3))

        val body = PracticeReminderBodyCalculator.notificationBody(dates, today)

        assertEquals(PracticeReminderBodySpec.KeepStreak(days = 3), body)
    }

    @Test
    fun `notification body reports broken streak when longest streak ended two days ago`() {
        val dates = setOf(daysAgo(2), daysAgo(3), daysAgo(4))

        val body = PracticeReminderBodyCalculator.notificationBody(dates, today)

        assertEquals(PracticeReminderBodySpec.StreakBroken(days = 3), body)
    }

    @Test
    fun `scheduled notification bodies project future reminders`() {
        val dates = setOf(today)

        val bodies = PracticeReminderBodyCalculator.scheduledNotificationBodies(
            dates = dates,
            days = 3,
            referenceTimeMillis = today
        )

        assertEquals(
            listOf(
                PracticeReminderBodySpec.PracticedToday,
                PracticeReminderBodySpec.KeepStreak(days = 1),
                PracticeReminderBodySpec.StreakBroken(days = 1)
            ),
            bodies
        )
    }

    private fun daysAgo(days: Int): Long = Calendar.getInstance().run {
        timeInMillis = today
        add(Calendar.DAY_OF_YEAR, -days)
        PracticeReminderBodyCalculator.startOfDay(timeInMillis)
    }

    private fun day(year: Int, month: Int, day: Int): Long = Calendar.getInstance().run {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}
