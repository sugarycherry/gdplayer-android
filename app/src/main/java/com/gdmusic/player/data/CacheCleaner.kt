package com.gdmusic.player.data

import android.content.Context
import coil.Coil
import com.gdmusic.player.player.CoverUrls
import com.gdmusic.player.player.LyricsCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object CacheCleaner {

    fun size(context: Context): Long {
        val dirs = listOfNotNull(context.cacheDir, context.externalCacheDir)
        return dirs.sumOf { dir ->
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    suspend fun clear(context: Context): Long = withContext(Dispatchers.IO) {
        val before = size(context)

        runCatching {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        LyricsCache.clear()
        CoverUrls.clear()
        MusicApi.clearCaches()
        runCatching {
            File(context.getExternalFilesDir(null), "update").deleteRecursively()
        }
        before
    }

    fun format(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        bytes > 0L -> "$bytes B"
        else -> "0 B"
    }
}
