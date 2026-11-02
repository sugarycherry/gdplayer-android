package com.gdmusic.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.gdmusic.player.player.LyricsCache
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

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var lyrics by remember(song?.key) {
        mutableStateOf(LyricsCache.get(song?.key) ?: emptyList())
    }

    LaunchedEffect(song?.key) {
        val current = song ?: return@LaunchedEffect
        val cached = LyricsCache.get(current.key)
        if (cached != null) {
            lyrics = cached
            return@LaunchedEffect
        }
        lyrics = emptyList()
        val result = MusicApi.lyric(current)
        val parsed = LrcParser.parse(result?.lrc, result?.translated)
        LyricsCache.putLines(current.key, parsed)
        lyrics = parsed
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
    val lyricScale = (LocalConfiguration.current.screenWidthDp / 400f).coerceIn(0.85f, 1.25f)
    val silentInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = silentInteraction,
                indication = null
            ) { }
    ) {
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

            val lyricIndex = if (lyrics.isEmpty()) -1
            else lyrics.indexOfLast { it.timeMs <= state.positionMs }
            val prevLyric = lyrics.getOrNull(lyricIndex - 1)?.text.orEmpty()
            val currentLyric = lyrics.getOrNull(lyricIndex)?.text.orEmpty()
            val nextLyric = lyrics.getOrNull(lyricIndex + 1)?.text.orEmpty()

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(
                        interactionSource = silentInteraction,
                        indication = null
                    ) { showLyrics = !showLyrics },
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics && lyrics.isNotEmpty()) {
                    LyricsView(
                        lines = lyrics,
                        positionMs = state.positionMs,
                        modifier = Modifier.fillMaxSize(),
                        activeColor = onDark,
                        inactiveColor = onDarkSecondary,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (18f * lyricScale).sp
                        ),
                        translationStyle = MaterialTheme.typography.bodySmall.copy(
                            fontSize = (13f * lyricScale).sp
                        )
                    )
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val discSize = minOf(maxWidth * 0.86f, maxHeight * 0.70f)
                        val blockScale = (maxWidth.value / 400f).coerceIn(0.85f, 1.25f)
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.weight(1f))
                            VinylDisc(
                                song = song,
                                playing = state.isPlaying,
                                modifier = Modifier.size(discSize)
                            )
                            Spacer(Modifier.height(18.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 112.dp)
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (prevLyric.isNotBlank()) {
                                    Text(
                                        text = prevLyric,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = (16f * blockScale).sp,
                                        color = onDarkSecondary.copy(alpha = 0.62f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                if (currentLyric.isNotBlank()) {
                                    Text(
                                        text = currentLyric,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = (22f * blockScale).sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = onDark,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                if (nextLyric.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = nextLyric,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = (16f * blockScale).sp,
                                        color = onDarkSecondary.copy(alpha = 0.62f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Spacer(Modifier.weight(0.45f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Box(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = 52.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onOpenQueue() }
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = onDarkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            val keyword = song.artist.substringBefore("/").trim()
                                .ifBlank { song.displayArtist }
                            onSearchArtist(keyword)
                        }
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
                        imageVector = if (state.shouldPlay) AppIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.shouldPlay) "暂停" else "播放",
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
            Spacer(Modifier.height(18.dp))
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
