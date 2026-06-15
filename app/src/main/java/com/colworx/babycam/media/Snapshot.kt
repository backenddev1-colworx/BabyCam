package com.colworx.babycam.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * Snapshot saves a single video frame (Bitmap) into the device's public gallery
 * under the "Pictures/BabyCam" album. Fully self-contained and reusable.
 */
object Snapshot {

    /**
     * Saves the given bitmap to the gallery as a JPEG.
     *
     * @return the content [Uri] of the saved image, or null if saving failed.
     */
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String = "BabyCam_" + System.currentTimeMillis()
    ): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/BabyCam"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var uri: Uri? = null
        return try {
            uri = resolver.insert(collection, values) ?: return null

            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    throw IllegalStateException("Failed to compress bitmap")
                }
            } ?: throw IllegalStateException("Failed to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            uri
        } catch (e: Exception) {
            // Roll back the orphaned MediaStore entry on failure.
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            null
        }
    }
}
