package com.colworx.babycam.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.colworx.babycam.MainActivity

/**
 * A lightweight foreground service that keeps the parent's process alive in the background so
 * the already-running MQTT subscription (owned by [com.colworx.babycam.webrtc.BabyCamConnection]
 * via the [com.colworx.babycam.webrtc.LiveSession] singleton) can keep receiving "cry" signals —
 * and [CryNotifier] can post a heads-up alert — even when the screen is off or another app is in
 * the foreground. This service does NOT own the MQTT/WebRTC connection itself; it only protects
 * the process from being background-killed and shows a low-priority "listening" notification so
 * the user understands why the app is still running.
 *
 * Also doubles as the thing that keeps the baby's audio (and video, while the live screen is
 * open) playing when the parent backgrounds the app or locks the screen — WebRTC's audio
 * playback isn't tied to any Activity/View, so as long as this service keeps the process alive,
 * `AudioTrack` playback from the remote peer just keeps running.
 *
 * Started when the parent enters a live session (ParentLiveScreen) and stopped on disconnect.
 */
class ParentMonitorService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false); acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        isRunning = true
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("open_parent_live", true)
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setContentTitle("BabyCam")
            .setContentText("Listening for baby alerts")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SERVICE, "Parent monitoring", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val NOTIF_ID = 2001
        const val CHANNEL_SERVICE = "babycam_parent_monitor"
        private const val WAKE_LOCK_TAG = "BabyCam::ParentMonitor"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
