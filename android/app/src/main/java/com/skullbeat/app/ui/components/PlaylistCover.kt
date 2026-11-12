package com.skullbeat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.skullbeat.app.domain.models.Playlist
import java.io.File

@Composable
fun PlaylistCover(
    playlist: Playlist?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    emojiSizeSp: Int = (size.value * 0.44f).toInt().coerceAtLeast(16)
) {
    PlaylistCover(
        coverPath = playlist?.coverPath,
        emoji = playlist?.emoji ?: "🎵",
        colorHex = playlist?.colorHex ?: "#3D8BFF",
        size = size,
        modifier = modifier,
        shape = shape,
        emojiSizeSp = emojiSizeSp
    )
}

@Composable
fun PlaylistCover(
    coverPath: String?,
    emoji: String,
    colorHex: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    emojiSizeSp: Int = (size.value * 0.44f).toInt().coerceAtLeast(16)
) {
    val bgColor = remember(colorHex) {
        try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {
            Color(0xFF3D8BFF)
        }
    }

    val fallbackContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = emojiSizeSp.sp)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
    ) {
        val hasCustomCover = remember(coverPath) {
            !coverPath.isNullOrBlank() && File(coverPath).exists()
        }

        if (hasCustomCover && coverPath != null) {
            SubcomposeAsyncImage(
                model = File(coverPath),
                contentDescription = "Playlist Cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { fallbackContent() },
                error = { fallbackContent() }
            )
        } else {
            fallbackContent()
        }
    }
}
