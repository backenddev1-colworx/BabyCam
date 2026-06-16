package com.colworx.babycam.webrtc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * Compose host for a WebRTC [SurfaceViewRenderer]. Renders [track] using [eglContext]
 * (from [BabyCamConnection.eglContext]). [mirror] is used for the front camera / local preview.
 *
 * Properly removes the old track's sink when the track changes or the composable leaves,
 * preventing memory leaks and stale frame delivery.
 */
@Composable
fun VideoRenderer(
    track: VideoTrack?,
    eglContext: EglBase.Context,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
) {
    val rendererRef = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    // The track currently wired to the renderer's sink. Used so we ONLY add/remove the sink
    // when the track actually changes — never on every recomposition.
    val sinkedTrack = remember { mutableStateOf<VideoTrack?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                setEnableHardwareScaler(true)
            }.also { rendererRef.value = it }
        },
        onReset = {},
    )

    // CRITICAL: attach/detach the sink only when `track` (or the renderer view) changes.
    //
    // This used to live in AndroidView's `update` lambda, which Compose runs on EVERY
    // recomposition. The parent screen recomposes ~once a second (battery updates) and on
    // every control toggle, so `track.addSink(view)` was being called over and over with the
    // SAME sink. libwebrtc keeps a list of sinks and happily adds duplicates, so each video
    // frame ended up dispatched to hundreds of duplicate sinks — CPU pegged (system load >25),
    // the main thread starved, and the app ANR'd / hung (notably right when taking a snapshot,
    // which added yet another sink + heavy work on top). Keying the effect on the track fixes it.
    val view = rendererRef.value
    DisposableEffect(view, track) {
        if (view != null && track != null) {
            track.addSink(view)
            sinkedTrack.value = track
        }
        onDispose {
            val v = rendererRef.value
            val t = sinkedTrack.value
            if (v != null && t != null) {
                try { t.removeSink(v) } catch (_: Exception) {}
            }
            sinkedTrack.value = null
        }
    }
}
