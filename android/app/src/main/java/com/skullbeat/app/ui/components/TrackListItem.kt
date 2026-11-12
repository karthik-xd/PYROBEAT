package com.skullbeat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.HeartRed
import com.skullbeat.app.ui.theme.RaisedDark
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted

@Composable
fun TrackListItem(
    track: Track,
    isCurrentTrack: Boolean = false,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onDownload: () -> Unit = {},
    onArtistClick: ((String) -> Unit)? = null,
    onSwipeToQueue: (() -> Unit)? = null,
    onSwipeAction: ((SwipeAction) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val hasSwipe = onSwipeToQueue != null || onSwipeAction != null
    val rowModifier = if (hasSwipe) Modifier else modifier
    val itemContent = @Composable {
        Row(
            modifier = rowModifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCurrentTrack) RaisedDark else SurfaceDark)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(RaisedDark)
                    .clickable {
                        if (onArtistClick != null) {
                            onArtistClick(track.cleanArtist)
                        } else {
                            onClick()
                        }
                    },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (isCurrentTrack) ElectricBlue else TextBone,
                        fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (track.durationFormatted.isNotBlank()) {
                        Text(
                            text = " • ${track.durationFormatted}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) HeartRed else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (onSwipeToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Queue") },
                            onClick = {
                                showMenu = false
                                onSwipeToQueue()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = {
                            showMenu = false
                            onDownload()
                        }
                    )
                    if (onArtistClick != null) {
                        DropdownMenuItem(
                            text = { Text("Go to Artist") },
                            onClick = {
                                showMenu = false
                                onArtistClick(track.cleanArtist)
                            }
                        )
                    }
                }
            }
        }
    }

    if (hasSwipe) {
        SwipeToQueueBox(
            onSwipeToQueue = onSwipeToQueue ?: {},
            onTriggerAction = onSwipeAction,
            modifier = modifier
        ) {
            itemContent()
        }
    } else {
        itemContent()
    }
}
