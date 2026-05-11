package com.bfunkstudios.beatclikr.services

import java.util.Calendar

sealed interface PracticeReminderBodySpec {
    data object Default : PracticeReminderBodySpec
    data object PracticedToday : PracticeReminderBodySpec
    data class KeepStreak(val days: Int) : PracticeReminderBodySpec
    data class StreakBroken(val days: Int) : PracticeReminderBodySpec
}

object PracticeReminderBodyCalculator {

    fun notificationBody(dates: Set<Long>, referenceTimeMillis: Long = System.currentTimeMillis()): PracticeReminderBodySpec =
        projectedBody(dates, referenceTimeMillis)

    fun scheduledNotificationBodies(
        dates: Set<Long>,
        days: Int,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): List<PracticeReminderBodySpec> {
        val today = startOfDay(referenceTimeMillis)
        return (0 until days).map { offset ->
            projectedBody(dates, addDays(today, offset))
        }
    }

    private fun projectedBody(dates: Set<Long>, referenceTimeMillis: Long): PracticeReminderBodySpec {
        val refDay = startOfDay(referenceTimeMillis)
        val yesterday = addDays(refDay, -1)
        val twoDaysAgo = addDays(refDay, -2)

        if (dates.contains(refDay)) {
            return PracticeReminderBodySpec.PracticedToday
        }

        if (dates.contains(yesterday)) {
            var check = yesterday
            var streak = 0
            while (dates.contains(check)) {
                streak += 1
                check = addDays(check, -1)
            }
            return PracticeReminderBodySpec.KeepStreak(streak)
        }

        if (dates.contains(twoDaysAgo)) {
            var check = twoDaysAgo
            var brokenLen = 0
            while (dates.contains(check)) {
                brokenLen += 1
                check = addDays(check, -1)
            }
            if (brokenLen == longestStreak(dates)) {
                return PracticeReminderBodySpec.StreakBroken(brokenLen)
            }
        }

        return PracticeReminderBodySpec.Default
    }

    fun startOfDay(epochMillis: Long): Long = Calendar.getInstance().run {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    private fun addDays(epochMillis: Long, days: Int): Long = Calendar.getInstance().run {
        timeInMillis = epochMillis
        add(Calendar.DAY_OF_YEAR, days)
        startOfDay(timeInMillis)
    }

    private fun longestStreak(dates: Set<Long>): Int {
        if (dates.isEmpty()) return 0
        val sorted = dates.sorted()
        var bestLen = 1
        var curLen = 1
        for (i in 1 until sorted.size) {
            if (addDays(sorted[i - 1], 1) == sorted[i]) {
                curLen += 1
                if (curLen > bestLen) bestLen = curLen
            } else {
                curLen = 1
            }
        }
        return bestLen
    }
}
