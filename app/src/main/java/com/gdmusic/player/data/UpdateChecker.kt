package com.gdmusic.player.data

import com.gdmusic.player.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val apiAssetUrl: String,
    val assetId: Long,
    val size: Long,
    val pageUrl: String
)

object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String): List<Int> =
            value.trim().removePrefix("v").removePrefix("V")
                .split('.')
                .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }

        val a = parts(remote)
        val b = parts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GDPlayer")
            .apply {
                val token = BuildConfig.GITHUB_TOKEN
                if (token.isNotBlank()) header("Authorization", "token $token")
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("GDPlayer", "update check http ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JsonParser.parseString(body).asJsonObject
                val tag = json.get("tag_name")?.asString ?: return@withContext null
                android.util.Log.d("GDPlayer", "update check: latest=$tag")
                val notes = json.get("body")?.asString ?: ""
                val page = json.get("html_url")?.asString ?: ""

                val assets = json.getAsJsonArray("assets") ?: return@withContext null
                val asset = assets
                    .mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }
                    .firstOrNull { it.get("name")?.asString?.endsWith(".apk") == true }
                    ?: return@withContext null

                UpdateInfo(
                    version = tag,
                    notes = notes,
                    downloadUrl = asset.get("browser_download_url")?.asString ?: "",
                    apiAssetUrl = asset.get("url")?.asString ?: "",
                    assetId = asset.get("id")?.asLong ?: 0L,
                    size = asset.get("size")?.asLong ?: 0L,
                    pageUrl = page
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("GDPlayer", "update check failed", e)
            null
        }
    }
}
