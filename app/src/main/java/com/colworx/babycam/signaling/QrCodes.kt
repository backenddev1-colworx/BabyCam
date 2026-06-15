package com.colworx.babycam.signaling

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Helpers for turning pairing payloads (e.g. a [RoomToken]) into QR bitmaps.
 */
object QrCodes {

    /**
     * Encodes [content] into a square ARGB_8888 QR [Bitmap] of [sizePx] pixels
     * per side. Black modules are drawn on a white background.
     *
     * @throws IllegalArgumentException if the content cannot be encoded.
     */
    fun encodeToBitmap(content: String, sizePx: Int = 720): Bitmap {
        try {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return bitmap
        } catch (e: WriterException) {
            throw IllegalArgumentException("Unable to encode QR content", e)
        }
    }
}
