package com.colworx.babycam.webrtc

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.view.Surface
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.CameraVideoCapturer
import java.lang.reflect.Field
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * WebRTC's public Camera2 capturer API does not expose torch control. Update its active repeating
 * request instead of competing with the open camera through CameraManager.setTorchMode().
 */
internal object Camera2TorchController {
    private const val TAG = "BabyCam"

    fun hasActiveSession(capturer: CameraVideoCapturer?): Boolean =
        runCatching { capturer?.readField("currentSession") != null }.getOrDefault(false)

    fun setTorch(capturer: CameraVideoCapturer?, enabled: Boolean): Boolean {
        val session = runCatching { capturer?.readField("currentSession") }.getOrNull() ?: return false
        val handler = runCatching { session.readField("cameraThreadHandler") as Handler }.getOrNull()
            ?: return false
        val completed = CountDownLatch(1)
        var applied = false
        val update = Runnable {
            applied = runCatching {
                val device = session.readField("cameraDevice") as CameraDevice
                val surface = session.readField("surface") as Surface
                val captureSession = session.readField("captureSession") as CameraCaptureSession
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(surface)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(
                        CaptureRequest.FLASH_MODE,
                        if (enabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF,
                    )
                    set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                    )
                    val format = session.readField("captureFormat")
                        as? CameraEnumerationAndroid.CaptureFormat
                    val fpsUnitFactor = session.readField("fpsUnitFactor") as? Int
                    if (format != null && fpsUnitFactor != null && fpsUnitFactor > 0) {
                        set(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            Range(
                                format.framerate.min / fpsUnitFactor,
                                format.framerate.max / fpsUnitFactor,
                            ),
                        )
                    }
                }.build()
                captureSession.setRepeatingRequest(request, null, handler)
                true
            }.onFailure {
                Log.e(TAG, "Active camera torch update failed", it)
            }.getOrDefault(false)
            completed.countDown()
        }

        if (handler.looper == Looper.myLooper()) {
            update.run()
        } else if (!handler.post(update)) {
            return false
        }
        return completed.await(1500, TimeUnit.MILLISECONDS) && applied
    }

    private fun Any.readField(name: String): Any? {
        var type: Class<*>? = javaClass
        while (type != null) {
            val field: Field? = runCatching { type.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return field.get(this)
            }
            type = type.superclass
        }
        return null
    }
}
