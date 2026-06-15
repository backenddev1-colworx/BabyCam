package com.colworx.babycam.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-starts the [MonitorService] after device boot, but only if the user had
 * monitoring enabled before the device was powered off/rebooted.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Minimal, synchronous work only — starting the service is quick and
        // safe to do directly inside onReceive, so goAsync() is unnecessary.
        if (MonitorController.isMonitoringEnabled(context)) {
            MonitorController.start(context.applicationContext)
        }
    }
}
