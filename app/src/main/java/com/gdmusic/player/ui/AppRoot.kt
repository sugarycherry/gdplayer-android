package com.gdmusic.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gdmusic.player.BuildConfig
import com.gdmusic.player.data.Quality
import com.gdmusic.player.data.Settings
import com.gdmusic.player.data.Song
import com.gdmusic.player.data.Store
import com.gdmusic.player.data.UpdateChecker
import com.gdmusic.player.data.UpdateInfo
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
    var checkingUpdate by remember { mutableStateOf(false) }
    var pendingSearch by remember { mutableStateOf<String?>(null) }

    fun toast(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun checkUpdate(silent: Boolean) {
        if (checkingUpdate) return
        checkingUpdate = true
        scope.launch {
            val info = UpdateChecker.latest()
            checkingUpdate = false
            when {
                info == null -> if (!silent) toast("检查更新失败，请检查网络")
                UpdateChecker.isNewer(info.version, BuildConfig.APP_VERSION) -> updateInfo = info
                else -> if (!silent) toast("已是最新版本 ${BuildConfig.APP_VERSION}")
            }
        }
    }

    LaunchedEffect(Unit) { checkUpdate(silent = true) }

    fun startDownload(song: Song) {
        scope.launch {
            toast("开始下载：${song.name}")
            val path = Downloader.download(context, song, library.settings.bitrate)
            toast(if (path != null) "已保存到 $path" else "下载失败")
        }
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }

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
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("搜索") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(AppIcons.LibraryMusic, contentDescription = null) },
                        label = { Text("我的") }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                tab = 0
            },
            onToast = { toast(it) }
        )
    }

    if (settingsOpen) {
        SettingsDialog(
            settings = library.settings,
            onDismiss = { settingsOpen = false },
            onSave = { Store.saveSettings(it) },
            onCheckUpdate = {
                settingsOpen = false
                checkUpdate(silent = false)
            }
        )
    }

    updateInfo?.let { info ->
        UpdateDialog(
            info = info,
            currentVersion = BuildConfig.APP_VERSION,
            onDismiss = { updateInfo = null },
            onToast = { toast(it) }
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
private fun SettingsDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onSave: (Settings) -> Unit,
    onCheckUpdate: () -> Unit
) {
    var local by remember { mutableStateOf(settings) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "当前版本 ${BuildConfig.APP_VERSION}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        onSave(local)
                        onCheckUpdate()
                    }
                ) {
                    Text("检查更新")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(local)
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
