package com.gdmusic.player.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.gdmusic.player.data.MusicApi
import com.gdmusic.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object Downloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun download(context: Context, song: Song, bitrate: Int): String? =
        withContext(Dispatchers.IO) {
            val info = MusicApi.streamUrl(song, bitrate) ?: return@withContext null
            val extension = if (info.url.contains(".flac", true)) "flac" else "mp3"
            val fileName = sanitize("${song.displayArtist} - ${song.name}.$extension")
            val mime = if (extension == "flac") "audio/flac" else "audio/mpeg"

            val request = Request.Builder()
                .url(info.url)
                .header("User-Agent", "GDPlayer/1.0 (Android)")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Audio.Media.MIME_TYPE, mime)
                            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/GD Player")
                            put(MediaStore.Audio.Media.IS_MUSIC, 1)
                            put(MediaStore.Audio.Media.TITLE, song.name)
                            put(MediaStore.Audio.Media.ARTIST, song.displayArtist)
                            put(MediaStore.Audio.Media.ALBUM, song.album)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            values
                        ) ?: return@withContext null
                        resolver.openOutputStream(uri)?.use { output ->
                            body.byteStream().copyTo(output)
                        } ?: return@withContext null
                        "Music/GD Player/$fileName"
                    } else {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_MUSIC
                            ),
                            "GD Player"
                        )
                        if (!dir.exists()) dir.mkdirs()
                        val target = File(dir, fileName)
                        target.outputStream().use { output ->
                            body.byteStream().copyTo(output)
                        }
                        target.absolutePath
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
}
