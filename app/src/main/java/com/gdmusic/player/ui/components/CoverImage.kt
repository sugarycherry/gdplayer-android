package com.gdmusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gdmusic.player.data.MusicApi
import com.gdmusic.player.data.Song
import com.gdmusic.player.player.CoverUrls

@Composable
fun CoverImage(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    placeholderTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    var url by remember(song?.key) {
        mutableStateOf(song?.let { CoverUrls.get(it.key) })
    }
    LaunchedEffect(song?.key) {
        if (song == null || url != null) return@LaunchedEffect
        val resolved = MusicApi.coverUrl(song)
        if (resolved != null) {
            CoverUrls.put(song.key, resolved)
            url = resolved
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = song?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = com.gdmusic.player.ui.AppIcons.MusicNote,
                contentDescription = null,
                tint = placeholderTint,
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
    }
}
