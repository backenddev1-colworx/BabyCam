package com.colworx.babycam.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import org.webrtc.YuvHelper
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures a single frame from a live [VideoTrack] and decodes it into a [Bitmap].
 *
 * A one-shot [VideoSink] is attached to the track; the first frame it receives is
 * retained (its buffer ref-counted up) and handed off to a background thread for
 * conversion (I420 -> NV21 -> JPEG -> Bitmap) + rotation, then the sink detaches
 * itself. All native buffers are released; any failure yields a null bitmap rather
 * than throwing.
 *
 * IMPORTANT: [VideoSink.onFrame] is called while WebRTC holds its internal
 * native sink-list lock. Calling [VideoTrack.removeSink] from INSIDE [onFrame]
 * tries to re-acquire that same lock → deadlock on the WebRTC thread, which in
 * turn starves the main thread and causes an ANR. The sink removal is therefore
 * done from the executor thread AFTER the callback returns. A 3-second timeout
 * covers the case where the track stops delivering frames (e.g. camera standby).
 */
object FrameCapture {

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor()

    fun capture(track: VideoTrack, onBitmap: (Bitmap?) -> Unit) {
        val captured = AtomicBoolean(false)
        // Held as a var so the executor timeout closure can reference it.
        var sink: VideoSink? = null

        val s = VideoSink { frame ->
            // Only the very first frame is used; ignore the rest (AtomicBoolean
            // ensures at-most-once even if multiple frames arrive before the sink
            // is removed).
            if (!captured.compareAndSet(false, true)) return@VideoSink
            // Keep the frame alive past this callback's scope.
            frame.retain()
            executor.execute {
                try {
                    onBitmap(toBitmap(frame))
                } finally {
                    frame.release()
                    // Safe to call removeSink here — we are OFF the frame-delivery
                    // thread so there is no re-entrant lock acquisition.
                    sink?.let { runCatching { track.removeSink(it) } }
                }
            }
        }
        sink = s
        track.addSink(s)

        // Timeout: if the track delivers no frame within 3 s (e.g. camera in
        // standby, track not yet active), clean up and report failure.
        executor.schedule({
            if (captured.compareAndSet(false, true)) {
                runCatching { track.removeSink(s) }
                onBitmap(null)
            }
        }, 3, TimeUnit.SECONDS)
    }

    /**
     * Converts a WebRTC [frame] to a [Bitmap]. The frame is owned by the caller
     * and must NOT be released here — only the derived I420 buffer is.
     */
    private fun toBitmap(frame: VideoFrame): Bitmap? {
        val i420 = frame.buffer.toI420() ?: return null
        return try {
            val width = i420.width
            val height = i420.height
            val ySize = width * height
            val uvSize = ySize / 2

            val nv21 = ByteBuffer.allocateDirect(ySize + uvSize)
            val dstY = nv21.duplicate().apply { position(0); limit(ySize) }.slice()
            val dstVU = nv21.duplicate().apply { position(ySize); limit(ySize + uvSize) }.slice()

            YuvHelper.I420ToNV12(
                i420.dataY, i420.strideY,
                i420.dataV, i420.strideV,
                i420.dataU, i420.strideU,
                dstY, width,
                dstVU, width,
                width, height
            )

            val nv21Bytes = ByteArray(ySize + uvSize)
            nv21.position(0)
            nv21.get(nv21Bytes)

            val yuvImage = YuvImage(nv21Bytes, ImageFormat.NV21, width, height, null)
            val baos = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, baos)
            val jpeg = baos.toByteArray()

            val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null

            val rotation = frame.rotation
            if (rotation == 0) decoded
            else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }
        } catch (e: Exception) {
            null
        } finally {
            i420.release()
        }
    }
}
