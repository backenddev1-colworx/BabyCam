package com.colworx.babycam.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.colworx.babycam.MainActivity

/**
 * Posts a high-priority "baby is crying" notification on the parent device when a
 * `cry` signal arrives over MQTT. The parent process may not have created the alert
 * channel yet, so the channel is (re)created here on API >= O.
 */
object CryNotifier {

    private const val NOTIF_CRY_ID = 1002
    private const val CHANNEL_ALERTS = "babycam_alerts"

    fun postCryAlert(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, "Alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra("open_parent_live", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle("Baby is crying!")
            .setContentText("Tap to open the live view")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // Show full content on the lock screen — this alert needs to be seen immediately,
            // not redacted behind "new notification" on devices with that privacy setting.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        nm.notify(NOTIF_CRY_ID, notification)
    }
}
