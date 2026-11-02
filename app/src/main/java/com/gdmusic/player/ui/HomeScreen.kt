package com.gdmusic.player.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gdmusic.player.data.MusicApi
import com.gdmusic.player.data.MusicSources
import com.gdmusic.player.data.Settings
import com.gdmusic.player.data.Song
import com.gdmusic.player.data.Store
import com.gdmusic.player.ui.components.SongRow
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    settings: Settings,
    currentSongKey: String?,
    pendingSearch: String?,
    onSearchConsumed: () -> Unit,
    onSourceChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onToast: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val library by Store.state.collectAsState()
    val listState = rememberLazyListState()

    var query by rememberSaveable { mutableStateOf("") }
    val gson = remember { com.google.gson.Gson() }
    val songsSaver = androidx.compose.runtime.saveable.listSaver<List<Song>, String>(
        save = { songs -> songs.map { gson.toJson(it) } },
        restore = { saved -> saved.map { gson.fromJson(it, Song::class.java) } }
    )
    var results by rememberSaveable(stateSaver = songsSaver) {
        mutableStateOf(emptyList<Song>())
    }
    var listTitle by rememberSaveable { mutableStateOf("网易云热歌榜") }
    var loading by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var hasMore by rememberSaveable { mutableStateOf(false) }

    suspend fun loadHot() {
        loading = true
        val list = MusicApi.playlist("3778678")
        if (list.isNotEmpty()) {
            results = list
            listTitle = "网易云热歌榜"
            hasMore = false
        }
        loading = false
    }

    fun search(reset: Boolean) {
        if (query.isBlank()) return
        if (reset) {
            results = emptyList()
            listTitle = "正在搜索「$query」…"
        }
        loading = true
        scope.launch {
            val nextPage = if (reset) 1 else page + 1
            var list = MusicApi.search(settings.source, query, nextPage, 30)
            if (list == null) {
                kotlinx.coroutines.delay(800)
                list = MusicApi.search(settings.source, query, nextPage, 30)
            }
            if (list == null) {
                loading = false
                if (reset) {
                    results = emptyList()
                    listTitle = "「$query」搜索失败，请重试"
                }
                onToast("搜索失败，请稍后重试")
                return@launch
            }
            if (reset) {
                results = list
                page = 1
                if (list.isEmpty()) onToast("没有找到「$query」")
            } else {
                results = results + list
                page = nextPage
            }
            hasMore = list.size >= 30
            listTitle = "「$query」的搜索结果"
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (results.isEmpty()) loadHot()
    }

    LaunchedEffect(pendingSearch) {
        val keyword = pendingSearch
        if (keyword.isNullOrBlank()) return@LaunchedEffect
        query = keyword
        search(reset = true)
        onSearchConsumed()
    }

    LaunchedEffect(query, settings.source) {
        if (query.isBlank() && results.isEmpty()) loadHot()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !loading && last >= results.size - 3 && results.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && query.isNotBlank()) search(reset = false)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "GD Player",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "接入 music-api.gdstudio.xyz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MusicSources.supported.forEach { option ->
                val selected = settings.source == option.key
                FilterChip(
                    selected = selected,
                    onClick = { onSourceChange(option.key) },
                    label = { Text(option.label) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.surface,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索歌曲 / 歌手") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清空")
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    search(reset = true)
                }
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = listTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (results.isNotEmpty()) {
                TextButton(onClick = { onPlay(results, 0) }) { Text("全部播放") }
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(results, key = { index, song -> "${song.key}-$index" }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        highlighted = song.key == currentSongKey,
                        favorite = library.favorites.any { it.key == song.key },
                        onClick = { onPlay(results, index) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onToggleFavorite = { Store.toggleFavorite(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onDownload = { onDownload(song) }
                    )
                }
                if (loading) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            if (results.isEmpty() && loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
