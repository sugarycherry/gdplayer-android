package com.gdmusic.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gdmusic.player.data.Song

@Composable
fun VinylDisc(
    song: Song?,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(playing) {
        while (playing) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(durationMillis = 24000, easing = LinearEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { rotationZ = rotation.value },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF26262A), Color(0xFF0C0C0E)),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )
            val ring = Color.White.copy(alpha = 0.05f)
            val ringStroke = Stroke(width = radius * 0.006f)
            for (i in 0..7) {
                drawCircle(ring, radius * (0.665f + i * 0.043f), style = ringStroke)
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = radius * 0.655f,
                style = Stroke(width = radius * 0.008f)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.55f),
                radius = radius * 0.645f
            )
        }
        CoverImage(
            song = song,
            modifier = Modifier.fillMaxSize(0.63f),
            shape = CircleShape,
            placeholderColor = Color(0xFF1B1B1E),
            placeholderTint = Color.White.copy(alpha = 0.35f)
        )
    }
}
