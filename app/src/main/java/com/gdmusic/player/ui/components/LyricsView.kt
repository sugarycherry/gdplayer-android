package com.gdmusic.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gdmusic.player.data.LyricLine

@Composable
fun LyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = remember(lines, positionMs) {
        if (lines.isEmpty()) 0 else lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(activeIndex, lines.size) {
        if (lines.isEmpty()) return@LaunchedEffect
        val target = activeIndex.coerceIn(0, lines.lastIndex)
        val layout = listState.layoutInfo
        val viewport = layout.viewportSize.height
        if (viewport <= 0) {
            listState.scrollToItem(target)
            return@LaunchedEffect
        }
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == target }
        val averageSize = layout.visibleItemsInfo
            .map { it.size }
            .average()
            .toInt()
            .takeIf { it > 0 } ?: 48
        val itemSize = visible?.size ?: averageSize
        val offset = itemSize / 2 - viewport / 2
        listState.animateScrollToItem(target, offset)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lines) { index, line ->
            val active = index == activeIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (!line.translation.isNullOrBlank()) {
                    Text(
                        text = line.translation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
