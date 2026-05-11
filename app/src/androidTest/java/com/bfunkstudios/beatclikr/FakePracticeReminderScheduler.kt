package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.IPracticeReminderScheduler

class FakePracticeReminderScheduler : IPracticeReminderScheduler {
    var rescheduleCount = 0
    var cancelCount = 0
    var rescheduleIfEnabledCount = 0

    override suspend fun reschedule() {
        rescheduleCount += 1
    }

    override fun cancel() {
        cancelCount += 1
    }

    override suspend fun rescheduleIfEnabled() {
        rescheduleIfEnabledCount += 1
    }

    override fun canScheduleExactAlarms(): Boolean = true
}
