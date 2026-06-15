package com.colworx.babycam.service

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Single entry point for starting/stopping the [MonitorService] and for
 * persisting whether monitoring should be running (used by boot auto-start).
 */
object MonitorController {

    private const val PREFS = "babycam_svc"
    private const val KEY_MONITORING = "monitoring"

    fun start(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
        setMonitoringEnabled(appContext, true)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MonitorService::class.java)
        appContext.stopService(intent)
        setMonitoringEnabled(appContext, false)
    }

    fun isMonitoringEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MONITORING, false)
    }

    private fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MONITORING, enabled)
            .apply()
    }
}
