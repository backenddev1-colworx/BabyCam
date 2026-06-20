package com.colworx.babycam.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads back the snapshots [Snapshot] has saved into the gallery, so the app can show an
 * in-app history instead of making the parent dig through the system Photos app. Snapshots are
 * identified purely by their "BabyCam_<timestamp>" display name (set in [Snapshot.saveToGallery])
 * — no separate index/database is kept, MediaStore is the single source of truth, so deleting a
 * photo from the system gallery also removes it here with no extra bookkeeping.
 */
object SnapshotGallery {

    data class Item(val uri: Uri, val dateAddedSec: Long)

    suspend fun list(context: Context): List<Item> = withContext(Dispatchers.IO) {
        val items = mutableListOf<Item>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("BabyCam_%")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        try {
            context.contentResolver.query(collection, projection, selection, args, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val date = cursor.getLong(dateCol)
                    items.add(Item(ContentUris.withAppendedId(collection, id), date))
                }
            }
        } catch (_: Exception) {
            // MediaStore query failed (permission revoked mid-session, etc.) — show an empty
            // gallery rather than crash; the snapshot button itself is unaffected.
        }
        items
    }

    suspend fun loadThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadFull(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** @return true if the row was actually removed. */
    suspend fun delete(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}
