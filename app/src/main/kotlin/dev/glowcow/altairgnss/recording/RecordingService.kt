package dev.glowcow.altairgnss.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import dev.glowcow.altairgnss.MainActivity
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps a recording going with the screen off: a foreground service with a wake lock, since the
 * barometer delivers nothing to a sleeping phone.
 */
class RecordingService : Service() {
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            container.recorder.stop()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: SecurityException) {
            // The location permission was taken away: nothing to record with.
            container.recorder.stop()
            return START_NOT_STICKY
        }
        if (job == null) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "altair:recording").apply { acquire(MAX_AWAKE_MS) }
            job = container.scope.launch { container.recorder.record() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_RECORDING, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_altair)
            .setContentTitle(getString(R.string.recording_notification))
            .setContentText(getString(R.string.recording_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setUsesChronometer(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.recording_stop), stop).build())
            .build()
    }

    private companion object {
        const val CHANNEL = "recording"
        const val NOTIFICATION_ID = 3
        const val ACTION_STOP = "dev.glowcow.altairgnss.STOP_RECORDING"
        // A recording longer than a day has been forgotten; the lock must not outlive it.
        const val MAX_AWAKE_MS = 24 * 60 * 60 * 1000L
    }
}
