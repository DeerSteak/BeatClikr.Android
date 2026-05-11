package com.bfunkstudios.beatclikr.services

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.db.PracticeHistoryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PracticeReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: IAppPreferences,
    private val practiceHistoryDao: PracticeHistoryDao
) : IPracticeReminderScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun reschedule() {
        cancel()
        if (!hasNotificationPermission()) return

        val dates = practiceHistoryDao.getAllSessions()
            .first()
            .map { PracticeReminderBodyCalculator.startOfDay(it.session.date) }
            .toSet()
        val bodies = PracticeReminderBodyCalculator.scheduledNotificationBodies(
            dates = dates,
            days = REMINDER_DAYS
        )
        bodies.forEachIndexed { index, bodySpec ->
            scheduleReminder(index = index, body = bodySpec.localizedBody())
        }
    }

    override fun cancel() {
        repeat(REMINDER_DAYS) { index ->
            alarmManager.cancel(pendingIntent(index, ""))
        }
    }

    override suspend fun rescheduleIfEnabled() {
        if (prefs.practiceReminderEnabled) {
            reschedule()
        } else {
            cancel()
        }
    }

    private fun scheduleReminder(index: Int, body: String) {
        val triggerMs = triggerAtMillis(index)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent(index, body))
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent(index, body))
        }
    }

    private fun pendingIntent(index: Int, body: String): PendingIntent {
        val intent = Intent(context, PracticeReminderNotificationReceiver::class.java).apply {
            action = PracticeReminderNotificationReceiver.ACTION_SHOW_REMINDER
            putExtra(PracticeReminderNotificationReceiver.EXTRA_INDEX, index)
            putExtra(PracticeReminderNotificationReceiver.EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun triggerAtMillis(dayOffset: Int): Long {
        val now = System.currentTimeMillis()
        return Calendar.getInstance().run {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, prefs.practiceReminderHour)
            set(Calendar.MINUTE, prefs.practiceReminderMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
            if (dayOffset == 0 && timeInMillis <= now) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            timeInMillis
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun PracticeReminderBodySpec.localizedBody(): String = when (this) {
        PracticeReminderBodySpec.Default ->
            context.getString(R.string.practice_reminder_notification_body)
        PracticeReminderBodySpec.PracticedToday ->
            context.getString(R.string.practice_reminder_notification_body_practiced_today)
        is PracticeReminderBodySpec.KeepStreak ->
            context.getString(R.string.practice_reminder_notification_body_keep_streak, days)
        is PracticeReminderBodySpec.StreakBroken ->
            context.getString(R.string.practice_reminder_notification_body_streak_broken, days)
    }

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    companion object {
        private const val REMINDER_DAYS = 7
    }
}
