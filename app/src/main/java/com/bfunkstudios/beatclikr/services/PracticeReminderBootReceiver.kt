package com.bfunkstudios.beatclikr.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bfunkstudios.beatclikr.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PracticeReminderBootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: IPracticeReminderScheduler
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            applicationScope.launch {
                try {
                    scheduler.rescheduleIfEnabled()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
