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
        update = { view -> track?.addSink(view) },
        onReset = {},
    )
    // When `track` changes, the previous key's onDispose fires first (removing old sink),
    // then the new effect adds the new sink via `update`.
    DisposableEffect(track) {
        onDispose {
            rendererRef.value?.let { view ->
                try { track?.removeSink(view) } catch (_: Exception) {}
            }
        }
    }
}
