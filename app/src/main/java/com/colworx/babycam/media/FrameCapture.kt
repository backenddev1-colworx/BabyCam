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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures a single frame from a live [VideoTrack] and decodes it into a [Bitmap].
 *
 * A one-shot [VideoSink] is attached to the track; the first frame it receives is
 * converted (I420 -> NV21 -> JPEG -> Bitmap), rotated to its display orientation,
 * then the sink detaches itself. All native buffers are released; any failure
 * yields a null bitmap rather than throwing.
 */
object FrameCapture {

    fun capture(track: VideoTrack, onBitmap: (Bitmap?) -> Unit) {
        val captured = AtomicBoolean(false)
        var sink: VideoSink? = null
        sink = VideoSink { frame ->
            // Only the very first frame is used; ignore the rest.
            if (!captured.compareAndSet(false, true)) return@VideoSink
            // Detach immediately so the track stops feeding us frames.
            sink?.let { track.removeSink(it) }
            onBitmap(toBitmap(frame))
        }
        track.addSink(sink)
    }

    /**
     * Converts a WebRTC [frame] to a [Bitmap]. The frame is owned by the caller
     * (the sink) and must NOT be released here — only the derived I420 buffer is.
     */
    private fun toBitmap(frame: VideoFrame): Bitmap? {
        val i420 = frame.buffer.toI420() ?: return null
        return try {
            val width = i420.width
            val height = i420.height
            val ySize = width * height
            val uvSize = ySize / 2 // interleaved chroma plane (NV21 V/U pairs)

            // Build NV21 directly via YuvHelper. This build of stream-webrtc-android
            // (1.3.8) exposes I420ToNV12 but not I420ToNV21; swapping the U and V
            // source planes turns the NV12 (UV) output into NV21 (VU) ordering.
            val nv21 = ByteBuffer.allocateDirect(ySize + uvSize)
            val dstY = nv21.duplicate().apply { position(0); limit(ySize) }.slice()
            val dstVU = nv21.duplicate().apply { position(ySize); limit(ySize + uvSize) }.slice()

            YuvHelper.I420ToNV12(
                i420.dataY, i420.strideY,
                // Swap V <-> U so the interleaved output is V,U = NV21.
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
            if (rotation == 0) {
                decoded
            } else {
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
