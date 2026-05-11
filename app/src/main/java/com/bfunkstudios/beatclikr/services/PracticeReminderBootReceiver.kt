package com.bfunkstudios.beatclikr.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PracticeReminderBootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: IPracticeReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scheduler.rescheduleIfEnabled()
        }
    }
}
