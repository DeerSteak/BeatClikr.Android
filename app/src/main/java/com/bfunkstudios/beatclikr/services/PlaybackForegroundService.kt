package com.bfunkstudios.beatclikr.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.os.IBinder
import java.io.FileDescriptor
import java.io.PrintWriter
import androidx.core.app.ServiceCompat
import com.bfunkstudios.beatclikr.MainActivity
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaybackForegroundService : Service() {
    @Inject lateinit var playback: PlaybackObservation
    @Inject lateinit var audio: IAudioPlayerService
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    private lateinit var mediaSession: MediaSession
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = createMediaSession()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        stateJob = scope.launch {
            playback.transportState.collect { state ->
                mediaSession.setPlaybackState(state.toSystemPlaybackState())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        playbackIntentForServiceAction(intent?.action)?.let(audio::submit)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        writer.println(PlaybackServiceDiagnostics.format(
            playback.transportState.value,
            audio.getFrameAudioMetricsSnapshot()
        ))
    }

    override fun onDestroy() {
        stateJob?.cancel()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    private fun buildNotification() = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.playback_notification_title))
        .setContentText(getString(R.string.playback_notification_active))
        .setContentIntent(activityIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_TRANSPORT)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken))
        .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        .addAction(stopAction())
        .build()

    private fun stopAction() = Notification.Action.Builder(
        Icon.createWithResource(this, R.drawable.ic_notification),
        getString(R.string.playback_notification_stop),
        stopIntent()
    ).build()

    private fun createMediaSession() = MediaSession(this, "BeatClikrPlayback").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPause() = stopPlayback()
            override fun onStop() = stopPlayback()
        })
        setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, getString(R.string.playback_notification_title))
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, getString(R.string.playback_notification_active))
                .build()
        )
        setPlaybackState(playback.transportState.value.toSystemPlaybackState())
        isActive = true
    }

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

    private fun stopPlayback() {
        audio.submit(PlaybackIntent.Stop)
    }

    companion object {
        const val ACTION_STOP = "com.bfunkstudios.beatclikr.action.STOP_PLAYBACK"
        private const val CHANNEL_ID = "active_playback"
        private const val NOTIFICATION_ID = 2001
    }
}
