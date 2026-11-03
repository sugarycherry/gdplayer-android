package com.gdmusic.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gdmusic.player.BuildConfig
import com.gdmusic.player.data.CacheCleaner
import com.gdmusic.player.data.MusicApi
import com.gdmusic.player.data.MusicSources
import com.gdmusic.player.data.Quality
import com.gdmusic.player.data.Settings
import com.gdmusic.player.data.Song
import com.gdmusic.player.data.Store
import com.gdmusic.player.data.UpdateChecker
import com.gdmusic.player.data.UpdateInfo
import com.gdmusic.player.data.UpdateResult
import com.gdmusic.player.download.Downloader
import com.gdmusic.player.player.PlayerConnection
import com.gdmusic.player.ui.components.AddToPlaylistDialog
import com.gdmusic.player.ui.components.MiniPlayer
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val library by Store.state.collectAsState()
    val playerState by PlayerConnection.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var pendingSearch by remember { mutableStateOf<String?>(null) }
    var queueOpen by remember { mutableStateOf(false) }

    fun toast(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun probeSources(verbose: Boolean) {
        scope.launch {
            if (verbose) toast("正在检测可用音源…")
            val working = MusicApi.probeSources()
            val current = Store.state.value.settings
            if (working.isEmpty()) {
                if (verbose) toast("检测失败，请检查网络或 API 地址")
            } else {
                Store.saveSettings(
                    current.copy(
                        availableSources = working,
                        sourcesProbed = true,
                        source = if (working.contains(current.source)) current.source
                        else working.first()
                    )
                )
                if (verbose) {
                    toast("可用音源：" + working.joinToString("、") { MusicSources.label(it) })
                }
            }
        }
    }

    fun checkUpdate(silent: Boolean) {
        if (checkingUpdate) return
        checkingUpdate = true
        scope.launch {
            val result = UpdateChecker.latest()
            checkingUpdate = false
            when (result) {
                is UpdateResult.Found -> {
                    if (UpdateChecker.isNewer(result.info.version, BuildConfig.APP_VERSION)) {
                        updateInfo = result.info
                    } else if (!silent) {
                        toast("已是最新版本 ${BuildConfig.APP_VERSION}")
                    }
                }
                is UpdateResult.Failed -> {
                    if (!silent) updateError = result.reason
                }
            }
        }
    }

    fun openInBrowser(url: String) {
        try {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast("无法打开浏览器")
        }
    }

    LaunchedEffect(Unit) { checkUpdate(silent = true) }
    LaunchedEffect(Unit) {
        if (!Store.state.value.settings.sourcesProbed) {
            kotlinx.coroutines.delay(2500)
            probeSources(verbose = false)
        }
    }

    fun startDownload(song: Song) {
        scope.launch {
            toast("开始下载：${song.name}")
            val path = Downloader.download(context, song, library.settings.bitrate)
            toast(if (path != null) "已保存到 $path" else "下载失败")
        }
    }

    BackHandler(enabled = playerOpen && !queueOpen) { playerOpen = false }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (playerState.song != null) {
                    MiniPlayer(
                        state = playerState,
                        onClick = { playerOpen = true },
                        onToggle = { PlayerConnection.toggle() },
                        onPrevious = { PlayerConnection.previous() },
                        onNext = { PlayerConnection.next() }
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(46.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactTab(
                            selected = tab == 0,
                            icon = Icons.Filled.Search,
                            label = "搜索",
                            onClick = { tab = 0 }
                        )
                        CompactTab(
                            selected = tab == 1,
                            icon = AppIcons.LibraryMusic,
                            label = "我的",
                            onClick = { tab = 1 }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val stateHolder = rememberSaveableStateHolder()
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            stateHolder.SaveableStateProvider(tab.toString()) {
                when (tab) {
                0 -> HomeScreen(
                    settings = library.settings,
                    currentSongKey = playerState.song?.key,
                    pendingSearch = pendingSearch,
                    onSearchConsumed = { pendingSearch = null },
                    onSourceChange = {
                        Store.saveSettings(library.settings.copy(source = it))
                    },
                    onOpenSettings = { settingsOpen = true },
                    onPlay = { songs, index ->
                        PlayerConnection.play(songs, index)
                        playerOpen = true
                    },
                    onPlayNext = { PlayerConnection.playNext(it); toast("已插入下一首") },
                    onAddToQueue = { PlayerConnection.addToQueue(it); toast("已加入队列") },
                    onAddToPlaylist = { playlistTarget = it },
                    onDownload = { startDownload(it) },
                    onToast = { toast(it) }
                )

                else -> LibraryScreen(
                    currentSongKey = playerState.song?.key,
                    onPlay = { songs, index ->
                        PlayerConnection.play(songs, index)
                        playerOpen = true
                    },
                    onPlayNext = { PlayerConnection.playNext(it); toast("已插入下一首") },
                    onAddToQueue = { PlayerConnection.addToQueue(it); toast("已加入队列") },
                    onAddToPlaylist = { playlistTarget = it },
                    onDownload = { startDownload(it) },
                    onToast = { toast(it) }
                )
                }
            }
        }
    }

    if (playerOpen && playerState.song != null) {
        PlayerScreen(
            state = playerState,
            settings = library.settings,
            onClose = { playerOpen = false },
            onQualityChange = { bitrate ->
                Store.saveSettings(library.settings.copy(bitrate = bitrate))
                PlayerConnection.changeQuality(bitrate)
                toast("已切换到 ${Quality.label(bitrate)}")
            },
            onAddToPlaylist = { playlistTarget = it },
            onDownload = { startDownload(it) },
            onSearchArtist = { artist ->
                pendingSearch = artist
                playerOpen = false
                queueOpen = false
                tab = 0
            },
            onOpenQueue = { queueOpen = true },
            onToast = { toast(it) }
        )
    }

    if (queueOpen) {
        QueueScreen(
            state = playerState,
            onBack = { queueOpen = false },
            onSelect = { index ->
                PlayerConnection.seekToQueueItem(index)
                queueOpen = false
            },
            onRemove = { PlayerConnection.removeFromQueue(it) }
        )
    }

    if (settingsOpen) {
        SettingsDialog(
            settings = library.settings,
            onDismiss = { settingsOpen = false },
            onSave = { next ->
                val baseChanged = next.apiBase != Store.state.value.settings.apiBase
                Store.saveSettings(
                    if (baseChanged) next.copy(sourcesProbed = false)
                    else next
                )
                if (baseChanged) probeSources(verbose = false)
            },
            onCheckUpdate = {
                settingsOpen = false
                checkUpdate(silent = false)
            },
            onClearCache = {
                scope.launch {
                    val freed = CacheCleaner.clear(context)
                    toast("已清理 ${CacheCleaner.format(freed)} 缓存")
                }
            },
            onProbeSources = { probeSources(verbose = true) }
        )
    }

    updateInfo?.let { info ->
        UpdateDialog(
            info = info,
            currentVersion = BuildConfig.APP_VERSION,
            onDismiss = { updateInfo = null },
            onToast = { toast(it) },
            onOpenPage = { openInBrowser(it.ifBlank { UpdateChecker.releasePageUrl() }) }
        )
    }

    updateError?.let { reason ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { updateError = null },
            title = { Text("检查更新失败") },
            text = {
                Text(
                    "原因：$reason\n\n" +
                        "可以直接到 GitHub 发布页用浏览器下载最新安装包。"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        updateError = null
                        openInBrowser(UpdateChecker.releasePageUrl())
                    }
                ) { Text("在浏览器打开") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        updateError = null
                        checkUpdate(silent = false)
                    }
                ) { Text("重试") }
            }
        )
    }

    playlistTarget?.let { song ->
        AddToPlaylistDialog(
            playlists = library.playlists,
            onDismiss = { playlistTarget = null },
            onPick = { id ->
                Store.addToPlaylist(id, song)
                playlistTarget = null
                toast("已加入歌单")
            },
            onCreate = { name ->
                val playlist = Store.createPlaylist(name)
                Store.addToPlaylist(playlist.id, song)
                toast("已创建并加入歌单")
            }
        )
    }
}

@Composable
private fun CompactTab(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
            else androidx.compose.ui.text.font.FontWeight.Normal
        )
    }
}

@Composable
private fun SettingsDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onSave: (Settings) -> Unit,
    onCheckUpdate: () -> Unit,
    onClearCache: () -> Unit,
    onProbeSources: () -> Unit
) {
    val context = LocalContext.current
    var local by remember { mutableStateOf(settings) }
    var cacheSize by remember { mutableStateOf(-1L) }
    var apiBaseText by remember { mutableStateOf(settings.apiBase) }
    var probing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSize = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CacheCleaner.size(context)
        }
    }

    LaunchedEffect(settings.availableSources) { probing = false }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("音质", style = MaterialTheme.typography.titleSmall)
                Quality.options.forEach { (br, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { local = local.copy(bitrate = br) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = local.bitrate == br,
                            onClick = { local = local.copy(bitrate = br) }
                        )
                        Text(label)
                    }
                }
                SettingSwitch("自动换源", local.autoSource) {
                    local = local.copy(autoSource = it)
                }
                SettingSwitch("深色主题", local.darkTheme) {
                    local = local.copy(darkTheme = it)
                }
                Spacer(Modifier.height(10.dp))
                Text("接口地址", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = apiBaseText,
                    onValueChange = { apiBaseText = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = {
                        Text(MusicSources.DEFAULT_API, style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "留空用默认地址。腾讯/酷我/潮汐等源需要自建接口，把地址填在这里即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !probing) {
                            probing = true
                            onSave(local.copy(apiBase = apiBaseText.trim()))
                            onProbeSources()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("重新检测可用音源", modifier = Modifier.weight(1f))
                    Text(
                        text = if (probing) "检测中…"
                        else "${MusicSources.options(local.availableSources).size} 个可用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onClearCache()
                            cacheSize = 0L
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("清理缓存", modifier = Modifier.weight(1f))
                    Text(
                        text = if (cacheSize < 0) "" else CacheCleaner.format(cacheSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "当前版本 ${BuildConfig.APP_VERSION}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        onSave(local.copy(apiBase = apiBaseText.trim()))
                        onCheckUpdate()
                    }
                ) {
                    Text("检查更新")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(local.copy(apiBase = apiBaseText.trim()))
                onDismiss()
            }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
