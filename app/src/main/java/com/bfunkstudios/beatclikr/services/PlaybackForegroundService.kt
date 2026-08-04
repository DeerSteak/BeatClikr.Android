package com.bfunkstudios.beatclikr.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.bfunkstudios.beatclikr.MainActivity
import com.bfunkstudios.beatclikr.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackForegroundService : Service() {
    @Inject lateinit var commands: PlaybackServiceCommandHandler

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        commands.handle(intent?.action)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.playback_notification_title))
        .setContentText(getString(R.string.playback_notification_active))
        .setContentIntent(activityIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .addAction(
            0,
            getString(R.string.playback_notification_stop),
            stopIntent()
        )
        .build()

    private fun activityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, PlaybackForegroundService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.playback_notification_channel_description)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.bfunkstudios.beatclikr.action.STOP_PLAYBACK"
        private const val CHANNEL_ID = "active_playback"
        private const val NOTIFICATION_ID = 2001
    }
}

class PlaybackServiceCommandHandler @Inject constructor(
    private val playback: IAudioPlayerService
) {
    fun handle(action: String?) {
        if (action == PlaybackForegroundService.ACTION_STOP) {
            playback.submit(PlaybackIntent.Stop)
        }
    }
}
