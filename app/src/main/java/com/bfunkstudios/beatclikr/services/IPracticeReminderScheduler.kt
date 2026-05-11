package com.bfunkstudios.beatclikr.services

interface IPracticeReminderScheduler {
    fun reschedule()
    fun cancel()
    fun rescheduleIfEnabled()
    fun canScheduleExactAlarms(): Boolean
}
