package com.gdmusic.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CoverTheme {

    private val cache = LruCache<String, Int>(64)

    suspend fun of(context: Context, url: String?): Int? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        cache.get(url)?.let { return@withContext it }
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(32)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            val drawable = (result as? SuccessResult)?.drawable ?: return@withContext null
            val bitmap = drawable.toBitmap(32, 32)
            val color = averageColor(bitmap)
            bitmap.recycle()
            cache.put(url, color)
            color
        } catch (e: Exception) {
            null
        }
    }

    private fun averageColor(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var weightSum = 0.0

        for (pixel in pixels) {
            if (AndroidColor.alpha(pixel) < 200) continue
            AndroidColor.colorToHSV(pixel, hsv)
            val weight = (hsv[1] * hsv[1] * hsv[2]).coerceAtLeast(0.02f)
            r += AndroidColor.red(pixel) * weight
            g += AndroidColor.green(pixel) * weight
            b += AndroidColor.blue(pixel) * weight
            weightSum += weight
        }

        if (weightSum <= 0.0) return NEUTRAL

        val avg = AndroidColor.rgb(
            (r / weightSum).toInt().coerceIn(0, 255),
            (g / weightSum).toInt().coerceIn(0, 255),
            (b / weightSum).toInt().coerceIn(0, 255)
        )
        AndroidColor.colorToHSV(avg, hsv)
        if (hsv[1] < 0.12f) return NEUTRAL
        hsv[1] = hsv[1].coerceIn(0.38f, 0.72f)
        hsv[2] = 0.40f
        return AndroidColor.HSVToColor(hsv)
    }

    private const val NEUTRAL = 0xFF26262C.toInt()
}
