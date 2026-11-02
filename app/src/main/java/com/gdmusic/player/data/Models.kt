package com.gdmusic.player.data

import com.google.gson.annotations.SerializedName

data class Song(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val album: String = "",
    val source: String = "netease",
    @SerializedName("url_id") val urlId: String = "",
    @SerializedName("pic_id") val picId: String = "",
    @SerializedName("lyric_id") val lyricId: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val hasHiRes: Boolean = false
) {
    val key: String get() = "$source:$urlId"

    val displayArtist: String get() = artist.ifBlank { "未知歌手" }
}

data class StreamInfo(
    val url: String,
    val br: Int,
    val size: Long
)

data class Lyric(
    val lrc: String,
    val translated: String?
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String?
)

data class UserPlaylist(
    val id: String = "",
    val name: String = "",
    val songs: List<Song> = emptyList(),
    val source: String = "local",
    val sourceId: String = ""
)

data class PlaylistDetail(
    val name: String,
    val songs: List<Song>
)

data class Settings(
    val source: String = "netease",
    val bitrate: Int = 320,
    val autoSource: Boolean = true,
    val darkTheme: Boolean = true
)

data class LibraryState(
    val favorites: List<Song> = emptyList(),
    val history: List<Song> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val settings: Settings = Settings(),
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0
)

object MusicSources {
    val supported = listOf(
        SourceOption("netease", "网易云"),
        SourceOption("bilibili", "B站"),
        SourceOption("joox", "JOOX")
    )

    fun label(key: String): String =
        supported.firstOrNull { it.key == key }?.label ?: key
}

data class SourceOption(val key: String, val label: String)

object Quality {
    val options = listOf(
        128 to "128k",
        192 to "192k",
        320 to "320k",
        999 to "无损"
    )

    fun label(br: Int): String = options.firstOrNull { it.first == br }?.second ?: "${br}k"
}
