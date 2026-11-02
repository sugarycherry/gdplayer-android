package com.gdmusic.player.player

import com.gdmusic.player.data.Lyric
import com.gdmusic.player.data.LrcParser
import com.gdmusic.player.data.LyricLine

object LyricsCache {

    private val map = object : LinkedHashMap<String, List<LyricLine>>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<LyricLine>>?
        ): Boolean = size > 12
    }

    fun get(key: String?): List<LyricLine>? {
        if (key.isNullOrBlank()) return null
        return map[key]
    }

    fun put(key: String?, lyric: Lyric?) {
        if (key.isNullOrBlank()) return
        map[key] = LrcParser.parse(lyric?.lrc, lyric?.translated)
    }

    fun putLines(key: String?, lines: List<LyricLine>) {
        if (key.isNullOrBlank()) return
        map[key] = lines
    }

    fun clear() {
        map.clear()
    }
}
