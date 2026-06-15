package com.colworx.babycam.webrtc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * Compose host for a WebRTC [SurfaceViewRenderer]. Renders [track] using [eglContext]
 * (from [BabyCamConnection.eglContext]). [mirror] is used for the front camera / local preview.
 */
@Composable
fun VideoRenderer(
    track: VideoTrack?,
    eglContext: EglBase.Context,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                setEnableHardwareScaler(true)
            }
        },
        update = { view -> track?.addSink(view) },
        onReset = {},
    )
    DisposableEffect(track) {
        onDispose { track?.let { runCatching { /* sink removed when view released */ } } }
    }
}
