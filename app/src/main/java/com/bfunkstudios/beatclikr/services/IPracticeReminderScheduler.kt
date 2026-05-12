package com.bfunkstudios.beatclikr.services

interface IPracticeReminderScheduler {
    suspend fun reschedule()
    fun cancel()
    suspend fun rescheduleIfEnabled()
}
