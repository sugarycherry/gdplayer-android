package com.gdmusic.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.gdmusic.player.data.LrcParser
import com.gdmusic.player.data.LyricLine
import com.gdmusic.player.data.MusicApi
import com.gdmusic.player.data.Settings
import com.gdmusic.player.data.Song
import com.gdmusic.player.data.Store
import com.gdmusic.player.player.PlayerConnection
import com.gdmusic.player.player.PlayerState
import com.gdmusic.player.ui.components.CoverImage
import com.gdmusic.player.ui.components.LyricsView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    settings: Settings,
    onClose: () -> Unit,
    onQualityChange: (Int) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onDownload: (Song) -> Unit
) {
    val library by Store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val song = state.song

    var showLyrics by remember { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }
    var queueExpanded by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(AppIcons.ChevronDown, contentDescription = "收起")
                }
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        com.gdmusic.player.data.Quality.options.forEach { (br, label) ->
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
                        DropdownMenuItem(
                            text = { Text("播放队列") },
                            onClick = { menuExpanded = false; queueExpanded = true }
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
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CoverImage(
                        song = song,
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = {
                        Store.toggleFavorite(song)
                    }
                ) {
                    val isFavorite = library.favorites.any { it.key == song.key }
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val duration = state.durationMs.coerceAtLeast(1L)
            val sliderValue = when {
                dragging -> dragValue
                else -> (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            }

            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    PlayerConnection.seekTo((dragValue * duration).toLong())
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formatTime(if (dragging) (dragValue * duration).toLong() else state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerConnection.toggleShuffle() }) {
                    Icon(
                        AppIcons.Shuffle,
                        contentDescription = "随机播放",
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { PlayerConnection.previous() }) {
                    Icon(AppIcons.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = { PlayerConnection.toggle() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = { PlayerConnection.next() }) {
                    Icon(AppIcons.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { PlayerConnection.cycleRepeat() }) {
                    Icon(
                        imageVector = when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> AppIcons.RepeatOne
                            Player.REPEAT_MODE_ALL -> AppIcons.Repeat
                            else -> AppIcons.Repeat
                        },
                        contentDescription = "循环模式",
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (queueExpanded) {
        ModalBottomSheet(onDismissRequest = { queueExpanded = false }) {
            Text(
                text = "播放队列（${state.queue.size}）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(state.queue) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                PlayerConnection.seekToQueueItem(index)
                                queueExpanded = false
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (index == state.index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                item.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { PlayerConnection.removeFromQueue(index) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "移除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
