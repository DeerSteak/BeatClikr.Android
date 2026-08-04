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
import android.media.session.PlaybackState
import android.os.IBinder
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
    @Inject lateinit var commands: PlaybackServiceCommandHandler
    @Inject lateinit var playback: PlaybackObservation
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
        commands.handle(intent?.action)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            override fun onPause() = commands.stop()
            override fun onStop() = commands.stop()
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
            stop()
        }
    }

    fun stop() {
        playback.submit(PlaybackIntent.Stop)
    }
}

internal fun PlaybackTransportState.toSystemPlaybackState(): PlaybackState {
    val projection = toSystemPlaybackProjection()
    val state = when (projection.status) {
        SystemPlaybackStatus.STOPPED -> PlaybackState.STATE_STOPPED
        SystemPlaybackStatus.CONNECTING -> PlaybackState.STATE_CONNECTING
        SystemPlaybackStatus.PLAYING -> PlaybackState.STATE_PLAYING
        SystemPlaybackStatus.STOPPING -> PlaybackState.STATE_STOPPED
    }
    return PlaybackState.Builder()
        .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
        .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
        .build()
}

internal enum class SystemPlaybackStatus { STOPPED, CONNECTING, PLAYING, STOPPING }

internal data class SystemPlaybackProjection(
    val status: SystemPlaybackStatus,
    val canPause: Boolean = true,
    val canStop: Boolean = true,
    val canPlay: Boolean = false,
    val canSeek: Boolean = false,
    val canSkip: Boolean = false,
    val canChangeSpeed: Boolean = false
)

internal fun PlaybackTransportState.toSystemPlaybackProjection() = SystemPlaybackProjection(
    when (this) {
        PlaybackTransportState.Idle -> SystemPlaybackStatus.STOPPED
        is PlaybackTransportState.Preparing,
        is PlaybackTransportState.Starting -> SystemPlaybackStatus.CONNECTING
        is PlaybackTransportState.Playing -> SystemPlaybackStatus.PLAYING
        is PlaybackTransportState.Stopping -> SystemPlaybackStatus.STOPPING
        is PlaybackTransportState.Interrupted,
        is PlaybackTransportState.Failed -> SystemPlaybackStatus.STOPPED
    }
)
