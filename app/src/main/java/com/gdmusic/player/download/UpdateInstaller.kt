package com.gdmusic.player.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.gdmusic.player.BuildConfig
import com.gdmusic.player.data.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object UpdateInstaller {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val token = BuildConfig.GITHUB_TOKEN
        val useApi = token.isNotBlank() && info.apiAssetUrl.isNotBlank()

        val builder = Request.Builder()
            .header("User-Agent", "GDPlayer")
            .header("Accept", if (useApi) "application/octet-stream" else "*/*")
        if (useApi) {
            builder.url(info.apiAssetUrl).header("Authorization", "token $token")
        } else {
            builder.url(info.downloadUrl)
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val total = body.contentLength().takeIf { it > 0 } ?: info.size

                val dir = File(context.getExternalFilesDir(null), "update")
                if (!dir.exists() && !dir.mkdirs()) return@withContext null
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "gdplayer-${info.version}.apk")

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val percent = (written * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent.coerceIn(0, 100))
                                }
                            }
                        }
                        output.flush()
                    }
                }
                if (target.length() < 1024 * 100) {
                    target.delete()
                    return@withContext null
                }
                android.util.Log.d("GDPlayer", "update downloaded: ${target.name} ${target.length()} bytes")
                onProgress(100)
                target
            }
        } catch (e: Exception) {
            null
        }
    }

    fun install(context: Context, file: File) {
        val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        android.util.Log.d("GDPlayer", "install requested: ${file.name} canInstall=$canInstall")
        if (!canInstall) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            android.util.Log.d("GDPlayer", "install: launching installer for $uri")
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.w("GDPlayer", "install failed", e)
            throw IOException("无法打开安装器")
        }
    }
}
