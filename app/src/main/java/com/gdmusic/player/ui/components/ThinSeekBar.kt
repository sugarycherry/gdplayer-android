package com.gdmusic.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun ThinSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.28f)
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: progress).coerceIn(0f, 1f)

    fun positionToProgress(x: Float, width: Float): Float {
        if (width <= 0f) return 0f
        val radius = width * 0.02f
        return ((x - radius) / (width - radius * 2)).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var fraction = positionToProgress(down.position.x, size.width.toFloat())
                    dragging = fraction
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        fraction = positionToProgress(change.position.x, size.width.toFloat())
                        dragging = fraction
                        change.consume()
                    }
                    dragging = null
                    onSeek(fraction)
                }
            }
    ) {
        val lineHeight = 1.5.dp.toPx()
        val dotRadius = 4.dp.toPx()
        val centerY = size.height / 2f
        val startX = dotRadius
        val endX = size.width - dotRadius
        val trackWidth = endX - startX
        val corner = CornerRadius(lineHeight / 2f)

        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(startX, centerY - lineHeight / 2f),
            size = Size(trackWidth, lineHeight),
            cornerRadius = corner
        )
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(startX, centerY - lineHeight / 2f),
            size = Size(trackWidth * shown, lineHeight),
            cornerRadius = corner
        )
        drawCircle(
            color = activeColor,
            radius = dotRadius,
            center = Offset(startX + trackWidth * shown, centerY)
        )
    }
}
