package com.gdmusic.player.player

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import com.gdmusic.player.data.MusicApi
import com.gdmusic.player.data.Song
import com.gdmusic.player.data.Store
import kotlinx.coroutines.runBlocking
import java.io.IOException

object SongRegistry {

    private val songs = LinkedHashMap<String, Song>()

    fun put(song: Song): String {
        songs[song.key] = song
        return song.key
    }

    fun putAll(list: List<Song>) {
        list.forEach { songs[it.key] = it }
    }

    fun get(key: String): Song? = songs[key]
}

object MediaUris {

    const val SCHEME = "gdplayer"

    fun build(song: Song, bitrate: Int): Uri =
        Uri.parse("$SCHEME://play/${Uri.encode(song.source)}/${Uri.encode(song.urlId.ifBlank { song.id })}?br=$bitrate")

    fun songKey(uri: Uri): String {
        val segments = uri.pathSegments
        val source = segments.getOrNull(0) ?: "netease"
        val id = segments.getOrNull(1) ?: ""
        return "$source:$id"
    }

    fun bitrate(uri: Uri): Int = uri.getQueryParameter("br")?.toIntOrNull() ?: 320
}

class StreamResolver(private val context: Context) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != MediaUris.SCHEME) return dataSpec

        val key = MediaUris.songKey(uri)
        val bitrate = MediaUris.bitrate(uri)
        val settings = Store.state.value.settings
        val song = SongRegistry.get(key) ?: Song(
            id = key.substringAfter(":"),
            source = key.substringBefore(":"),
            urlId = key.substringAfter(":")
        )

        val resolved = runBlocking { MusicApi.streamUrl(song, bitrate) }
            ?: (if (settings.autoSource) runBlocking { MusicApi.autoSource(song, bitrate) } else null)
            ?: throw IOException("无法获取播放地址")

        return dataSpec.withUri(Uri.parse(resolved.url))
    }
}

fun streamDataSourceFactory(context: Context): DataSource.Factory =
    ResolvingDataSource.Factory(
        androidx.media3.datasource.DefaultDataSource.Factory(context),
        StreamResolver(context)
    )
