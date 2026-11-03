package com.gdmusic.player.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object MusicApi {

    private const val USER_AGENT = "GDPlayer/1.0 (Android)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val coverSemaphore = kotlinx.coroutines.sync.Semaphore(2)

    private const val SEARCH_CACHE_TTL_MS = 10 * 60 * 1000L

    private val searchCache = object : LinkedHashMap<String, Pair<Long, List<Song>>>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Pair<Long, List<Song>>>?
        ): Boolean = size > 24
    }

    private val coverCache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 200
    }

    private val coverThrottle = kotlinx.coroutines.sync.Mutex()
    private var lastCoverRequestAt = 0L

    private fun url(vararg params: Pair<String, String>): HttpUrl {
        val builder = MusicSources.DEFAULT_API.toHttpUrl().newBuilder()
        params.forEach { builder.addQueryParameter(it.first, it.second) }
        return builder.build()
    }

    suspend fun probeSources(): List<String> = withContext(Dispatchers.IO) {
        val working = mutableListOf<String>()
        for (option in MusicSources.all) {
            val raw = get(
                url(
                    "types" to "search",
                    "source" to option.key,
                    "name" to "love",
                    "count" to "1"
                )
            )
            if (raw != null && raw.trimStart().startsWith("[") && !raw.contains("not supported")) {
                working.add(option.key)
            }
            kotlinx.coroutines.delay(80)
        }
        working
    }

    private fun get(url: HttpUrl): String? {
        repeat(2) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return response.body?.string()
                    }
                    android.util.Log.w("GDPlayer", "http ${response.code} for $url")
                    if (response.code < 500) return null
                }
            } catch (e: Exception) {
                android.util.Log.w("GDPlayer", "request failed: $url", e)
                if (attempt == 1) return null
            }
            Thread.sleep(350L * (attempt + 1))
        }
        return null
    }

    suspend fun search(
        source: String,
        keyword: String,
        page: Int = 1,
        count: Int = 30
    ): List<Song>? = withContext(Dispatchers.IO) {
        val cacheKey = "$source|$keyword|$page|$count"
        searchCache[cacheKey]?.let { (cachedAt, songs) ->
            if (System.currentTimeMillis() - cachedAt < SEARCH_CACHE_TTL_MS) {
                return@withContext songs
            }
            searchCache.remove(cacheKey)
        }

        val raw = get(
            url(
                "types" to "search",
                "source" to source,
                "name" to keyword,
                "count" to count.toString(),
                "pages" to page.toString()
            )
        ) ?: return@withContext null

        val songs = parseSongArray(raw, source)
        if (songs.isNotEmpty()) {
            searchCache[cacheKey] = System.currentTimeMillis() to songs
        }
        songs
    }

    suspend fun playlist(id: String): List<Song> = playlistDetail(id)?.songs ?: emptyList()

    suspend fun playlistDetail(id: String): PlaylistDetail? = withContext(Dispatchers.IO) {
        val raw = get(url("types" to "playlist", "id" to id)) ?: return@withContext null
        try {
            val root = JsonParser.parseString(raw).asJsonObject
            val playlist = root.getAsJsonObject("playlist") ?: return@withContext null
            val name = playlist.get("name")?.takeIf { !it.isJsonNull }?.asString
                ?: return@withContext null
            val tracks = playlist.getAsJsonArray("tracks") ?: return@withContext PlaylistDetail(name, emptyList())
            val songs = tracks.mapNotNull { element ->
                val track = element as? JsonObject ?: return@mapNotNull null
                val artists = track.getAsJsonArray("ar")?.joinToString("/") {
                    it.asJsonObject.get("name")?.asString ?: ""
                } ?: ""
                val album = track.getAsJsonObject("al")
                val trackId = track.get("id")?.asString ?: return@mapNotNull null
                val picUrl = album?.get("picUrl")?.takeIf { !it.isJsonNull }?.asString
                Song(
                    id = trackId,
                    name = track.get("name")?.asString ?: "",
                    artist = artists,
                    album = album?.get("name")?.asString ?: "",
                    source = "netease",
                    urlId = trackId,
                    picId = picUrl ?: trackId,
                    lyricId = trackId,
                    durationMs = (track.get("dt")?.asLong ?: 0L),
                    coverUrl = picUrl?.let { normalize(it) },
                    hasHiRes = false
                )
            }
            PlaylistDetail(name, songs)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun streamUrl(song: Song, bitrate: Int): StreamInfo? = withContext(Dispatchers.IO) {
        val raw = get(
            url(
                "types" to "url",
                "source" to song.source,
                "id" to song.urlId.ifBlank { song.id },
                "br" to bitrate.toString()
            )
        ) ?: return@withContext null
        parseStream(raw)
    }

    suspend fun autoSource(song: Song, bitrate: Int): StreamInfo? = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("name", song.name)
            addProperty("artist", song.artist)
            addProperty("album", song.album)
            addProperty("duration", song.durationMs / 1000)
        }
        val raw = get(
            url(
                "types" to "autosource",
                "source" to song.source,
                "br" to bitrate.toString(),
                "name" to payload.toString()
            )
        ) ?: return@withContext null
        parseStream(raw)
    }

    suspend fun coverUrl(song: Song, size: Int = 600): String? = withContext(Dispatchers.IO) {
        song.coverUrl?.let { return@withContext normalize(it) }
        val cacheKey = "${song.key}:$size"
        coverCache[cacheKey]?.let { return@withContext it }

        coverSemaphore.withPermit {
            coverCache[cacheKey] ?: run {
                coverThrottle.withLock {
                    val wait = 140L - (System.currentTimeMillis() - lastCoverRequestAt)
                    if (wait > 0) kotlinx.coroutines.delay(wait)
                    lastCoverRequestAt = System.currentTimeMillis()
                }
                val raw = get(
                    url(
                        "types" to "pic",
                        "source" to song.source,
                        "id" to song.picId.ifBlank { song.id },
                        "size" to size.toString()
                    )
                )
                val link = raw?.let { body ->
                    try {
                        JsonParser.parseString(body).asJsonObject.get("url")?.asString
                    } catch (e: Exception) {
                        null
                    }
                }?.let { normalize(it) }
                if (link != null) coverCache[cacheKey] = link
                link
            }
        }
    }

    suspend fun lyric(song: Song): Lyric? = withContext(Dispatchers.IO) {
        val raw = get(
            url(
                "types" to "lyric",
                "source" to song.source,
                "id" to song.lyricId.ifBlank { song.id }
            )
        ) ?: return@withContext null
        try {
            val obj = JsonParser.parseString(raw).asJsonObject
            val lrc = obj.get("lyric")?.takeIf { !it.isJsonNull }?.asString ?: return@withContext null
            val translated = obj.get("tlyric")?.takeIf { !it.isJsonNull }?.asString
            Lyric(lrc, translated?.takeIf { it.isNotBlank() })
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStream(raw: String): StreamInfo? = try {
        val obj = JsonParser.parseString(raw).asJsonObject
        val link = obj.get("url")?.takeIf { !it.isJsonNull }?.asString
        if (link.isNullOrBlank() || link == "err") {
            null
        } else {
            StreamInfo(
                url = link,
                br = obj.get("br")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                size = obj.get("size")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun parseSongArray(raw: String, source: String): List<Song> {
        return try {
            val element: JsonElement = JsonParser.parseString(raw)
            if (!element.isJsonArray) return emptyList()
            element.asJsonArray.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val artists = obj.getAsJsonArray("artist")?.joinToString("/") {
                    it.asString
                } ?: ""
                val extra = obj.getAsJsonObject("extra_data")
                val picId = obj.get("pic_id")?.takeIf { !it.isJsonNull }?.asString ?: ""
                Song(
                    id = obj.get("id")?.asString ?: "",
                    name = obj.get("name")?.asString ?: "",
                    artist = artists,
                    album = obj.get("album")?.asString ?: "",
                    source = obj.get("source")?.asString ?: source,
                    urlId = obj.get("url_id")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    picId = picId,
                    lyricId = obj.get("lyric_id")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    durationMs = (extra?.get("duration")?.takeIf { !it.isJsonNull }?.asLong ?: 0L) * 1000,
                    coverUrl = picId.takeIf { it.startsWith("http") || it.startsWith("//") },
                    hasHiRes = extra?.get("has_hires")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun normalize(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        else -> url
    }

    fun clearCaches() {
        searchCache.clear()
        coverCache.clear()
    }
}
