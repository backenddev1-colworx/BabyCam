package com.colworx.babycam.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.colworx.babycam.R

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!MonitorController.isMonitoringEnabled(context)) return

        // Android 14+ (API 34) prohibits camera/microphone foreground services
        // from starting via BOOT_COMPLETED. Show a tap-to-resume notification instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            showResumeNotification(context)
        } else {
            MonitorController.start(context.applicationContext)
        }
    }

    private fun showResumeNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel("babycam_resume", "Resume Monitoring", NotificationManager.IMPORTANCE_HIGH)
        )

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        val pi = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, "babycam_resume")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BabyCam paused after restart")
            .setContentText("Tap to resume monitoring")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(9001, notif)
    }
}
