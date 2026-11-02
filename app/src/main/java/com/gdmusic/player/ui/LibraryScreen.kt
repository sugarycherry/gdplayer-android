package com.gdmusic.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gdmusic.player.data.MusicApi
import com.gdmusic.player.data.Song
import com.gdmusic.player.data.Store
import com.gdmusic.player.ui.components.SongRow
import kotlinx.coroutines.launch

private val tabTitles = listOf("收藏", "历史", "歌单")

@Composable
fun LibraryScreen(
    currentSongKey: String?,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onToast: (String) -> Unit
) {
    val library by Store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importLoading by remember { mutableStateOf(false) }

    val importedPlaylist = library.playlists.firstOrNull { it.source == "netease" }
    LaunchedEffect(importedPlaylist?.id) {
        if (openedId == null && importedPlaylist != null) {
            openedId = importedPlaylist.id
        }
    }

    val opened = library.playlists.firstOrNull { it.id == openedId }

    if (opened != null) {
        PlaylistDetail(
            name = opened.name,
            songs = opened.songs,
            currentSongKey = currentSongKey,
            onBack = { openedId = null },
            onPlay = onPlay,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onDownload = onDownload,
            onRemove = { song -> Store.removeFromPlaylist(opened.id, song.key) }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "我的音乐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (tab == 1 && library.history.isNotEmpty()) {
                TextButton(onClick = { Store.clearHistory() }) { Text("清空历史") }
            }
            if (tab == 2) {
                IconButton(onClick = { importing = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        AppIcons.Download,
                        contentDescription = "导入歌单",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { creating = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "新建歌单",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    modifier = Modifier.height(38.dp),
                    text = {
                        Text(title, style = MaterialTheme.typography.labelLarge)
                    }
                )
            }
        }

        val songs = when (tab) {
            0 -> library.favorites
            1 -> library.history
            else -> emptyList()
        }

        if (tab == 2) {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(library.playlists, key = { _, p -> p.id }) { _, playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openedId = playlist.id }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${playlist.songs.size} 首",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { Store.deletePlaylist(playlist.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除歌单",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (library.playlists.isEmpty()) {
                    item { EmptyHint("还没有歌单，点右上角 ＋ 新建，或点下载图标导入网易云歌单") }
                }
            }
            return@Column
        }

        if (songs.isEmpty()) {
            EmptyHint(
                if (tab == 0) "还没有收藏的歌曲" else "还没有播放记录"
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { index, song -> "${song.key}-$index" }) { index, song ->
                SongRow(
                    song = song,
                    index = index,
                    highlighted = song.key == currentSongKey,
                    favorite = library.favorites.any { it.key == song.key },
                    onClick = { onPlay(songs, index) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onToggleFavorite = {
                        Store.toggleFavorite(song)
                        onToast(if (Store.isFavorite(song)) "已收藏" else "已取消收藏")
                    },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onDownload = { onDownload(song) }
                )
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("歌单名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        Store.createPlaylist(newName.trim())
                        onToast("已创建歌单")
                    }
                    newName = ""
                    creating = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text("取消") }
            }
        )
    }

    if (importing) {
        AlertDialog(
            onDismissRequest = { if (!importLoading) importing = false },
            title = { Text("导入网易云歌单") },
            text = {
                Column {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        singleLine = true,
                        enabled = !importLoading,
                        label = { Text("歌单链接或 ID") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "例如 https://music.163.com/playlist?id=3778678",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (importLoading) {
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !importLoading,
                    onClick = {
                        val id = Regex("\\d{5,}").find(importText)?.value
                        if (id == null) {
                            onToast("请粘贴歌单链接或 ID")
                            return@TextButton
                        }
                        scope.launch {
                            importLoading = true
                            val detail = MusicApi.playlistDetail(id)
                            importLoading = false
                            if (detail == null || detail.songs.isEmpty()) {
                                onToast("导入失败，请检查歌单 ID")
                            } else {
                                Store.importPlaylist(detail.name, detail.songs, id)
                                onToast("已导入「${detail.name}」${detail.songs.size} 首")
                                importing = false
                                importText = ""
                            }
                        }
                    }
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(
                    enabled = !importLoading,
                    onClick = { importing = false }
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PlaylistDetail(
    name: String,
    songs: List<Song>,
    currentSongKey: String?,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onRemove: (Song) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${songs.size} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { if (songs.isNotEmpty()) onPlay(songs, 0) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "全部播放")
            }
        }
        if (songs.isEmpty()) {
            EmptyHint("歌单还是空的")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { index, song -> "${song.key}-$index" }) { index, song ->
                SongRow(
                    song = song,
                    index = index,
                    highlighted = song.key == currentSongKey,
                    onClick = { onPlay(songs, index) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onDownload = { onDownload(song) },
                    onRemove = { onRemove(song) }
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
