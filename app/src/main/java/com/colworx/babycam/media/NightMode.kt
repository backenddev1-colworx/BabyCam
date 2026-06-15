package com.colworx.babycam.media

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Reusable Night Mode helpers for low-light baby viewing.
 *
 * The visual treatment dims the underlying content and adds a slight warm/green
 * tint to evoke a classic night-vision feel, while keeping detail visible.
 */

// Translucent dark veil that reduces overall brightness.
private val NightDim = Color(0x80000000)

// Faint green wash for a night-vision tint (boosts the perceived green channel).
private val NightTint = Color(0x14193A1F)

/**
 * Applies a low-light treatment to any composable when [enabled] is true.
 *
 * Draws the original content, then overlays a translucent dark veil plus a
 * subtle green tint. No-op when disabled, so it's safe to leave in the tree.
 */
fun Modifier.nightModeFilter(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        this.drawWithContent {
            drawContent()
            drawRect(color = NightDim, topLeft = Offset.Zero, size = size)
            drawRect(color = NightTint, topLeft = Offset.Zero, size = size)
        }
    }

/**
 * Convenience state holder for a Night Mode toggle.
 */
@Composable
fun NightModeToggleState(): MutableState<Boolean> = remember { mutableStateOf(false) }

/**
 * Stand-alone overlay that draws a translucent dark + warm/green wash over the
 * area it occupies. Place it on top of the live view (e.g. inside a Box) and
 * size it via [modifier] to match the video surface.
 */
@Composable
fun NightOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    Canvas(modifier = modifier) {
        drawRect(color = NightDim, topLeft = Offset.Zero, size = size)
        drawRect(color = NightTint, topLeft = Offset.Zero, size = size)
    }
}
