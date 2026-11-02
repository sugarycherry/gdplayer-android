package com.gdmusic.player.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.gdmusic.player.data.MusicApi
import com.gdmusic.player.data.Song
import com.gdmusic.player.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque

data class PlayerState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val shouldPlay: Boolean = false,
    val isBuffering: Boolean = false,
    val song: Song? = null,
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

object PlayerConnection {

    private var controller: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ticker: Job? = null
    private var lastPrefetchIndex = -1

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var queue: List<Song> = emptyList()

    fun connect(context: Context) {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val mediaController = future.get()
                controller = mediaController
                mediaController.addListener(listener)
                syncFromController()
                startTicker()
            } catch (e: Exception) {
                android.util.Log.e("GDPlayer", "media controller connect failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromController()
            val index = player.currentMediaItemIndex
            if (index >= 0 && index != lastPrefetchIndex) {
                lastPrefetchIndex = index
                prefetchArtwork(index + 1)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val c = controller ?: return
            if (errorCount < 3 && c.hasNextMediaItem()) {
                errorCount++
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            } else {
                errorCount = 0
                c.pause()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) errorCount = 0
        }
    }

    private var errorCount = 0

    private fun currentBitrate(): Int = Store.state.value.settings.bitrate

    private fun toMediaItem(song: Song, bitrate: Int = currentBitrate()): MediaItem {
        SongRegistry.put(song)
        val artworkUrl = CoverUrls.get(song.key)
        return MediaItem.Builder()
            .setMediaId(song.key)
            .setUri(MediaUris.build(song, bitrate))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.displayArtist)
                    .setAlbumTitle(song.album)
                    .apply {
                        if (!artworkUrl.isNullOrBlank()) {
                            setArtworkUri(android.net.Uri.parse(artworkUrl))
                        }
                    }
                    .build()
            )
            .build()
    }

    private fun syncFromController() {
        val c = controller ?: return
        if (queue.isEmpty() && c.mediaItemCount > 0) {
            queue = (0 until c.mediaItemCount).map { index ->
                val id = c.getMediaItemAt(index).mediaId
                SongRegistry.get(id)
                    ?: Song(id = id.substringAfter(":"), urlId = id.substringAfter(":"))
            }
        }
        val index = c.currentMediaItemIndex.coerceAtLeast(0)
        val song = queue.getOrNull(index)
        _state.value = _state.value.copy(
            connected = true,
            isPlaying = c.isPlaying,
            shouldPlay = c.playWhenReady,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            song = song,
            queue = queue,
            index = index,
            durationMs = if (c.duration > 0) c.duration else (song?.durationMs ?: 0L),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode
        )
    }

    private fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    val position = c.currentPosition.coerceAtLeast(0)
                    if (position != _state.value.positionMs) {
                        _state.value = _state.value.copy(
                            positionMs = position,
                            durationMs = if (c.duration > 0) c.duration else _state.value.durationMs
                        )
                    }
                }
                delay(500)
            }
        }
    }

    fun play(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        queue = songs
        SongRegistry.putAll(songs)
        val index = startIndex.coerceIn(0, songs.lastIndex)
        _state.value = _state.value.copy(queue = songs, index = index, song = songs[index])
        Store.setQueue(songs, index)

        val items = songs.map { toMediaItem(it) }
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
        prefetchArtwork(index)
    }

    private fun prefetchArtwork(from: Int) {
        if (from < 0) return
        scope.launch {
            for (offset in 0 until 3) {
                val index = from + offset
                val song = queue.getOrNull(index) ?: continue
                if (CoverUrls.get(song.key) != null) continue
                val url = MusicApi.coverUrl(song) ?: continue
                CoverUrls.put(song.key, url)
                val c = controller ?: return@launch
                if (index >= c.mediaItemCount) continue
                if (index == c.currentMediaItemIndex) continue
                if (c.getMediaItemAt(index).mediaId == song.key) {
                    c.replaceMediaItem(index, toMediaItem(song))
                }
            }
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun seekToQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.seekTo(index, 0L)
    }

    fun cyclePlayMode(): String {
        val c = controller ?: return "列表循环"
        val mode = when {
            c.repeatMode == Player.REPEAT_MODE_ONE -> 2
            c.shuffleModeEnabled -> 1
            else -> 0
        }
        val next = when (mode) {
            0 -> {
                c.shuffleModeEnabled = true
                c.repeatMode = Player.REPEAT_MODE_ALL
                "随机播放"
            }
            1 -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_ONE
                "单曲循环"
            }
            else -> {
                c.shuffleModeEnabled = false
                c.repeatMode = Player.REPEAT_MODE_ALL
                "列表循环"
            }
        }
        return next
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        val insertAt = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItem(insertAt, toMediaItem(song))
        queue = queue.toMutableList().apply { add(insertAt, song) }
        _state.value = _state.value.copy(queue = queue)
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        c.addMediaItem(toMediaItem(song))
        queue = queue + song
        _state.value = _state.value.copy(queue = queue)
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        c.removeMediaItem(index)
        queue = queue.toMutableList().apply { removeAt(index) }
        _state.value = _state.value.copy(queue = queue)
    }

    fun changeQuality(bitrate: Int) {
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        val song = queue.getOrNull(index) ?: return
        val position = c.currentPosition
        val wasPlaying = c.isPlaying
        c.replaceMediaItem(index, toMediaItem(song, bitrate))
        c.seekTo(index, position)
        if (wasPlaying) c.play()
    }
}

object CoverUrls {

    private val map = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 60
    }

    private val pending = ArrayDeque<String>()

    fun put(key: String, url: String) {
        if (url.isNotBlank()) map[key] = url
    }

    fun get(key: String): String? = map[key]

    fun markPending(key: String): Boolean {
        if (pending.contains(key)) return false
        pending.addLast(key)
        if (pending.size > 40) pending.removeFirst()
        return true
    }

    fun clear() {
        map.clear()
        pending.clear()
    }
}
