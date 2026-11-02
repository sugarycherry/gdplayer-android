package com.gdmusic.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun vector(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    paths.forEach { builder.addPath(pathData = addPathNodes(it), fill = SolidColor(Color.White)) }
    return builder.build()
}

object AppIcons {
    val Pause by lazy { vector("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }
    val SkipNext by lazy { vector("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z") }
    val SkipPrevious by lazy { vector("SkipPrevious", "M6,6h2v12H6zM9.5,12l8.5,6V6z") }
    val Shuffle by lazy {
        vector(
            "Shuffle",
            "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z"
        )
    }
    val Repeat by lazy {
        vector(
            "Repeat",
            "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z"
        )
    }
    val RepeatOne by lazy {
        vector(
            "RepeatOne",
            "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z",
            "M13,15V9h-1l-2,1v1h1.5v4H13z"
        )
    }
    val Download by lazy { vector("Download", "M5,20h14v-2H5v2zM19,9h-4V3H9v6H5l7,7 7,-7z") }
    val QueueMusic by lazy {
        vector(
            "QueueMusic",
            "M15,6H3v2h12V6zM15,10H3v2h12V10zM3,16h8v-2H3v2zM17,6v8.18C16.69,14.07 16.35,14 16,14c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3V8h3V6h-5z"
        )
    }
    val MusicNote by lazy {
        vector(
            "MusicNote",
            "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"
        )
    }
    val LibraryMusic by lazy {
        vector(
            "LibraryMusic",
            "M20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM18,7h-3v5.5c0,1.38 -1.12,2.5 -2.5,2.5S10,13.88 10,12.5s1.12,-2.5 2.5,-2.5c0.57,0 1.08,0.19 1.5,0.51V5h4v2zM4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6z"
        )
    }
    val PlaylistAdd by lazy {
        vector(
            "PlaylistAdd",
            "M14,10H2v2h12v-2zM14,6H2v2h12V6zM2,16h8v-2H2v2zM19,6v8.18c-0.31,-0.11 -0.65,-0.18 -1,-0.18 -1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3V8h3V6h-5z"
        )
    }
    val ChevronDown by lazy {
        vector("ChevronDown", "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z")
    }
    val ChevronUp by lazy {
        vector("ChevronUp", "M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6 1.41,1.41z")
    }
    val VolumeUp by lazy {
        vector(
            "VolumeUp",
            "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"
        )
    }
}
