package com.colworx.babycam.service

import android.content.Context
import android.content.Intent
import android.os.Build

/** Single entry point for starting/stopping [ParentMonitorService]. */
object ParentMonitorController {

    fun start(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, ParentMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, ParentMonitorService::class.java))
    }
}
