package com.colworx.babycam.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.colworx.babycam.MainActivity
import com.colworx.babycam.audio.CryDetector
import com.colworx.babycam.audio.Sensitivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitorService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var audioJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var lowBatteryAlerted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false); acquire() }
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildServiceNotification(), type)
        isRunning = true
        startAudioMonitor()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        stopAudioMonitor()
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    // ── Audio / cry detection ─────────────────────────────────────────────────

    private fun startAudioMonitor() {
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuf == AudioRecord.ERROR_BAD_VALUE) 4096 else minBuf.coerceAtLeast(4096)

        val ar = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: return
        audioRecord = ar

        val detector = CryDetector(Sensitivity.MEDIUM)
        val frame = ShortArray(bufferSize / 2)

        audioJob = scope.launch {
            ar.startRecording()
            while (isActive) {
                val read = ar.read(frame, 0, frame.size)
                if (read > 0 && detector.process(frame.copyOf(read))) {
                    sendAlertNotification(
                        id = NOTIF_CRY_ID,
                        title = "Baby is crying!",
                        text = "Tap to open BabyCam"
                    )
                }
            }
            runCatching { ar.stop() }
            runCatching { ar.release() }
        }
    }

    private fun stopAudioMonitor() {
        audioJob?.cancel()
        audioJob = null
        audioRecord = null
    }

    // ── Battery monitoring ────────────────────────────────────────────────────

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (scale > 0) level * 100 / scale else -1
                if (pct in 1..LOW_BATTERY_PCT && !lowBatteryAlerted) {
                    lowBatteryAlerted = true
                    sendAlertNotification(
                        id = NOTIF_BATT_ID,
                        title = "Baby phone battery low",
                        text = "Battery at $pct% — plug in soon"
                    )
                } else if (pct > LOW_BATTERY_PCT) {
                    lowBatteryAlerted = false
                }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun buildServiceNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setContentTitle("BabyCam")
            .setContentText("Monitoring is active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun sendAlertNotification(id: Int, title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SERVICE, "Monitoring", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, "Alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    companion object {
        const val NOTIF_ID = 1001
        const val NOTIF_CRY_ID = 1002
        const val NOTIF_BATT_ID = 1003
        const val CHANNEL_SERVICE = "babycam_monitor"
        const val CHANNEL_ALERTS = "babycam_alerts"
        const val LOW_BATTERY_PCT = 20
        private const val WAKE_LOCK_TAG = "BabyCam::Monitor"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
