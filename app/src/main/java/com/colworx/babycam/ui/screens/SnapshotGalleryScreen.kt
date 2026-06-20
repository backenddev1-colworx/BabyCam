package com.colworx.babycam.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.colworx.babycam.media.SnapshotGallery
import com.colworx.babycam.ui.theme.Indigo900
import com.colworx.babycam.ui.theme.IndigoDeep
import com.colworx.babycam.ui.theme.Lavender100
import com.colworx.babycam.ui.theme.Lavender50
import com.colworx.babycam.ui.theme.Muted
import kotlinx.coroutines.launch

/**
 * In-app history of every snapshot taken from the live feed (see [SnapshotGallery]). Snapshots
 * still land in the system Photos app too (that's unchanged) — this is just a convenient
 * in-app view so the parent doesn't have to leave BabyCam to find them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SnapshotGalleryScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<SnapshotGallery.Item>?>(null) }
    val thumbnails = remember { mutableStateMapOf<String, Bitmap?>() }
    var viewing by remember { mutableStateOf<SnapshotGallery.Item?>(null) }
    var pendingDelete by remember { mutableStateOf<SnapshotGallery.Item?>(null) }

    suspend fun reload() {
        items = SnapshotGallery.list(context)
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(items) {
        items?.forEach { item ->
            val key = item.uri.toString()
            if (!thumbnails.containsKey(key)) {
                thumbnails[key] = SnapshotGallery.loadThumbnail(context, item.uri)
            }
        }
    }

    BackHandler(enabled = viewing != null) { viewing = null }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete snapshot?") },
            text = { Text("This removes it from your gallery permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        if (SnapshotGallery.delete(context, target.uri)) {
                            thumbnails.remove(target.uri.toString())
                            if (viewing?.uri == target.uri) viewing = null
                            reload()
                        }
                    }
                }) { Text("Delete", color = androidx.compose.ui.graphics.Color(0xFFE24B4A)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    val full = viewing
    if (full != null) {
        SnapshotViewer(
            item = full,
            onBack = { viewing = null },
            onShare = { shareSnapshot(context, full) },
            onDelete = { pendingDelete = full },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = IndigoDeep)
            }
            Text(
                text = "Snapshots",
                style = MaterialTheme.typography.titleLarge,
                color = Indigo900,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IndigoDeep)
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = Lavender100,
                        modifier = Modifier.height(56.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No snapshots yet", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.uri.toString() }) { item ->
                    val bmp = thumbnails[item.uri.toString()]
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Lavender100)
                            .combinedClickable(
                                onClick = { viewing = item },
                                onLongClick = { pendingDelete = item },
                            )
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotViewer(
    item: SnapshotGallery.Item,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var full by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) { full = SnapshotGallery.loadFull(context, item.uri) }

    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        full?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = androidx.compose.ui.graphics.Color.White)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = androidx.compose.ui.graphics.Color(0xFFFF8B8A))
            }
        }
    }
}

private fun shareSnapshot(context: android.content.Context, item: SnapshotGallery.Item) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share snapshot"))
}
