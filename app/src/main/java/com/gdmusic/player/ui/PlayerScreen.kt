package com.gdmusic.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gdmusic.player.data.LrcParser
import com.gdmusic.player.data.LyricLine
import com.gdmusic.player.data.MusicApi
import com.gdmusic.player.data.Quality
import com.gdmusic.player.data.Settings
import com.gdmusic.player.data.Song
import com.gdmusic.player.data.Store
import com.gdmusic.player.player.CoverUrls
import com.gdmusic.player.player.PlayerConnection
import com.gdmusic.player.player.PlayerState
import com.gdmusic.player.ui.components.LyricsView
import com.gdmusic.player.ui.components.ThinSeekBar
import com.gdmusic.player.ui.components.VinylDisc

@Composable
fun PlayerScreen(
    state: PlayerState,
    settings: Settings,
    onClose: () -> Unit,
    onQualityChange: (Int) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onSearchArtist: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onToast: (String) -> Unit
) {
    val library by Store.state.collectAsState()
    val context = LocalContext.current
    val song = state.song

    var showLyrics by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }

    LaunchedEffect(song?.key) {
        lyrics = emptyList()
        if (song != null) {
            val result = MusicApi.lyric(song)
            lyrics = LrcParser.parse(result?.lrc, result?.translated)
        }
    }

    if (song == null) {
        return
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view)
                .isAppearanceLightStatusBars = false
        }
        onDispose {
            window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, view)
                    .isAppearanceLightStatusBars = !settings.darkTheme
            }
        }
    }

    val coverUrl = CoverUrls.get(song.key) ?: song.coverUrl
    val onDark = Color.White
    val onDarkSecondary = Color.White.copy(alpha = 0.72f)

    Box(Modifier.fillMaxSize()) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(56.dp)
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF1B1B1E)))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.32f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(AppIcons.ChevronDown, contentDescription = "收起", tint = onDark)
                }
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.titleSmall,
                    color = onDarkSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = onDark
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        Quality.options.forEach { (br, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (settings.bitrate == br) "音质：$label ✓" else "音质：$label"
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onQualityChange(br)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("下一首播放") },
                            onClick = {
                                menuExpanded = false
                                PlayerConnection.playNext(song)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("加入歌单") },
                            onClick = { menuExpanded = false; onAddToPlaylist(song) }
                        )
                        DropdownMenuItem(
                            text = { Text("下载") },
                            onClick = { menuExpanded = false; onDownload(song) }
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable { showLyrics = !showLyrics },
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics && lyrics.isNotEmpty()) {
                    LyricsView(
                        lines = lyrics,
                        positionMs = state.positionMs,
                        modifier = Modifier.fillMaxSize(),
                        activeColor = onDark,
                        inactiveColor = onDarkSecondary,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        translationStyle = MaterialTheme.typography.bodySmall
                    )
                } else {
                    VinylDisc(
                        song = song,
                        playing = state.isPlaying,
                        modifier = Modifier.fillMaxWidth(0.74f)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Box(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onOpenQueue() }
                            .padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onDarkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable {
                                val keyword = song.artist.substringBefore("/").trim()
                                    .ifBlank { song.displayArtist }
                                onSearchArtist(keyword)
                            }
                            .padding(horizontal = 8.dp)
                    )
                }
                IconButton(
                    onClick = { Store.toggleFavorite(song) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    val isFavorite = library.favorites.any { it.key == song.key }
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else onDarkSecondary
                    )
                }
            }

            val duration = state.durationMs.coerceAtLeast(1L)
            val sliderValue = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)

            ThinSeekBar(
                progress = sliderValue,
                onSeek = { fraction ->
                    PlayerConnection.seekTo((fraction * duration).toLong())
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formatTime(state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = onDarkSecondary
                )
                Text(
                    text = formatTime(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = onDarkSecondary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onToast(PlayerConnection.cyclePlayMode()) }) {
                    val modeIcon = when {
                        state.repeatMode == Player.REPEAT_MODE_ONE -> AppIcons.RepeatOne
                        state.shuffle -> AppIcons.Shuffle
                        else -> AppIcons.Repeat
                    }
                    Icon(modeIcon, contentDescription = "播放模式", tint = onDark)
                }
                IconButton(onClick = { PlayerConnection.previous() }) {
                    Icon(
                        AppIcons.SkipPrevious,
                        contentDescription = "上一首",
                        tint = onDark,
                        modifier = Modifier.size(34.dp)
                    )
                }
                FilledIconButton(
                    onClick = { PlayerConnection.toggle() },
                    modifier = Modifier.size(64.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = onDark,
                        contentColor = Color(0xFF141416)
                    )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = { PlayerConnection.next() }) {
                    Icon(
                        AppIcons.SkipNext,
                        contentDescription = "下一首",
                        tint = onDark,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = { onOpenQueue() }) {
                    Icon(AppIcons.QueueMusic, contentDescription = "播放列表", tint = onDark)
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
