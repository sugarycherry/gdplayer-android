package com.gdmusic.player.data

object LrcParser {

    private val TIME_TAG = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(lrc: String?, translation: String? = null): List<LyricLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        val translated = parseRaw(translation)
        val lines = parseRaw(lrc)
        if (lines.isEmpty()) return emptyList()

        return lines.map { (time, text) ->
            LyricLine(time, text, translated[time])
        }.sortedBy { it.timeMs }
    }

    private fun parseRaw(content: String?): LinkedHashMap<Long, String> {
        val result = LinkedHashMap<Long, String>()
        if (content.isNullOrBlank()) return result
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val matches = TIME_TAG.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0
                val seconds = match.groupValues[2].toLongOrNull() ?: 0
                val fractionRaw = match.groupValues[3]
                val fraction = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLong() * 100
                    2 -> fractionRaw.toLong() * 10
                    else -> fractionRaw.take(3).toLong()
                }
                val time = minutes * 60_000 + seconds * 1_000 + fraction
                if (text.isNotEmpty()) result[time] = text
            }
        }
        return result
    }
}
