package com.colworx.babycam.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.colworx.babycam.R

const val BOOT_COMPLETED_ACTION = Intent.ACTION_BOOT_COMPLETED

fun shouldOfferBootResume(
    action: String?,
    monitoringEnabled: Boolean,
    autoStartEnabled: Boolean,
): Boolean = action == BOOT_COMPLETED_ACTION && monitoringEnabled && autoStartEnabled

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldOfferBootResume(
                action = intent.action,
                monitoringEnabled = MonitorController.isMonitoringEnabled(context),
                autoStartEnabled = MonitorController.isAutoStartEnabled(context),
            )
        ) return

        // Reboot never activates camera or microphone in the background. The user must open the
        // app, pass the app lock if configured, and let normal saved-session restoration resume.
        showResumeNotification(context)
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
            .setContentText("Tap to unlock and resume monitoring")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(9001, notif)
    }
}
