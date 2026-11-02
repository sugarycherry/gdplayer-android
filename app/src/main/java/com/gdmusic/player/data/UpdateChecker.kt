package com.gdmusic.player.data

import com.gdmusic.player.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class UpdateInfo(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val apiAssetUrl: String,
    val assetId: Long,
    val size: Long,
    val pageUrl: String
)

sealed class UpdateResult {
    data class Found(val info: UpdateInfo) : UpdateResult()
    data class Failed(val reason: String) : UpdateResult()
}

object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .build()

    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
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

    fun releasePageUrl(): String = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest"

    suspend fun latest(): UpdateResult = withContext(Dispatchers.IO) {
        var reason = "未知错误"

        try {
            return@withContext UpdateResult.Found(fetchFromApi())
        } catch (e: Exception) {
            reason = describe(e)
        }

        try {
            return@withContext UpdateResult.Found(fetchFromReleasePage())
        } catch (e: Exception) {
            reason = "$reason / 备用通道：${describe(e)}"
        }

        UpdateResult.Failed(reason)
    }

    private fun describe(error: Exception): String = when (error) {
        is SocketTimeoutException -> "连接超时"
        is UnknownHostException -> "域名解析失败（DNS 被拦截）"
        is SSLException -> "TLS 握手失败"
        is IOException -> error.message?.takeIf { it.isNotBlank() } ?: "网络错误"
        else -> error.javaClass.simpleName + ": " + (error.message ?: "")
    }

    private fun fetchFromApi(): UpdateInfo {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GDPlayer")
            .apply {
                val token = BuildConfig.GITHUB_TOKEN
                if (token.isNotBlank()) header("Authorization", "token $token")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub API 返回 HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("GitHub API 响应为空")
            val json = JsonParser.parseString(body).asJsonObject
            val tag = json.get("tag_name")?.asString ?: throw IOException("解析版本号失败")
            val notes = json.get("body")?.asString ?: ""
            val page = json.get("html_url")?.asString ?: releasePageUrl()

            val assets = json.getAsJsonArray("assets") ?: throw IOException("没有发布文件")
            val asset = assets
                .mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }
                .firstOrNull { it.get("name")?.asString?.endsWith(".apk") == true }
                ?: throw IOException("发布里没有 APK")

            return UpdateInfo(
                version = tag,
                notes = notes,
                downloadUrl = asset.get("browser_download_url")?.asString ?: "",
                apiAssetUrl = asset.get("url")?.asString ?: "",
                assetId = asset.get("id")?.asLong ?: 0L,
                size = asset.get("size")?.asLong ?: 0L,
                pageUrl = page
            )
        }
    }

    private fun fetchFromReleasePage(): UpdateInfo {
        val request = Request.Builder()
            .url("https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("User-Agent", "GDPlayer")
            .build()

        noRedirectClient.newCall(request).execute().use { response ->
            val location = response.header("Location")
                ?: throw IOException("GitHub 页面返回 HTTP ${response.code}")
            val tag = location.trimEnd('/').substringAfterLast("/")
            if (!tag.startsWith("v") && !tag.startsWith("V")) {
                throw IOException("无法从跳转地址解析版本号")
            }
            return UpdateInfo(
                version = tag,
                notes = "",
                downloadUrl = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/" +
                    "$tag/GDMusicPlayer-$tag.apk",
                apiAssetUrl = "",
                assetId = 0L,
                size = 0L,
                pageUrl = location
            )
        }
    }
}
