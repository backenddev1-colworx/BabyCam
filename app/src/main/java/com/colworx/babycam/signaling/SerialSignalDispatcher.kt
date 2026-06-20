package com.colworx.babycam.signaling

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SerialSignalDispatcher {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BabyCam-MQTT-Send").apply { isDaemon = true }
    }

    fun dispatch(action: () -> Unit) {
        if (!executor.isShutdown) executor.execute(action)
    }

    fun close() {
        executor.shutdownNow()
    }
}
