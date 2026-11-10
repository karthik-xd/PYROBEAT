package com.skullbeat.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.skullbeat.app.R
import com.skullbeat.app.domain.models.Playlist
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.ui.dialogs.AboutDialog
import com.skullbeat.app.ui.theme.CyanAccent
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.HeartRed
import com.skullbeat.app.ui.theme.RaisedDark
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted
import com.skullbeat.app.ui.theme.VioletAccent

@Composable
fun HomeScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onPlaylistClick: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val historyTracks by viewModel.historyTracks.collectAsState()
    val madeForYouTracks by viewModel.madeForYouTracks.collectAsState()
    val featuredTracks by viewModel.featuredTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val greeting = remember { viewModel.getGreeting() }
    var showAboutDialog by remember { mutableStateOf(false) }

    val recentItems = remember(historyTracks, featuredTracks) {
        if (historyTracks.isNotEmpty()) historyTracks else featuredTracks.take(8)
    }

    val forYouItems = remember(madeForYouTracks, featuredTracks) {
        if (madeForYouTracks.isNotEmpty()) madeForYouTracks else featuredTracks.drop(8).take(8)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Screen Title: "Good evening" + v1.1.0 about pill
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextBone
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(RaisedDark)
                        .clickable { showAboutDialog = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "v1.1.0",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricBlue
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // 2x2 Playlist Tiles Grid
        item {
            val tileGradients = listOf(
                Brush.linearGradient(listOf(CyanAccent, ElectricBlue)),
                Brush.linearGradient(listOf(ElectricBlue, VioletAccent)),
                Brush.linearGradient(listOf(RaisedDark, ElectricBlue)),
                Brush.linearGradient(listOf(VioletAccent, HeartRed))
            )

            val displayPlaylists = remember(playlists) {
                val list = mutableListOf<Pair<String, Long?>>()
                playlists.take(4).forEach { list.add(it.name to it.id) }
                val defaults = listOf("Synthwave Run", "Night Drive", "Phonk Gym", "Cyber Chill")
                while (list.size < 4) {
                    list.add(defaults[list.size] to null)
                }
                list
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlaylistGridTile(
                        title = displayPlaylists[0].first,
                        gradient = tileGradients[0],
                        modifier = Modifier.weight(1f),
                        onClick = { displayPlaylists[0].second?.let(onPlaylistClick) }
                    )
                    PlaylistGridTile(
                        title = displayPlaylists[1].first,
                        gradient = tileGradients[1],
                        modifier = Modifier.weight(1f),
                        onClick = { displayPlaylists[1].second?.let(onPlaylistClick) }
                    )
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlaylistGridTile(
                        title = displayPlaylists[2].first,
                        gradient = tileGradients[2],
                        modifier = Modifier.weight(1f),
                        onClick = { displayPlaylists[2].second?.let(onPlaylistClick) }
                    )
                    PlaylistGridTile(
                        title = displayPlaylists[3].first,
                        gradient = tileGradients[3],
                        modifier = Modifier.weight(1f),
                        onClick = { displayPlaylists[3].second?.let(onPlaylistClick) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Section: Recently played
        item {
            Text(
                text = "Recently played",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextBone
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (recentItems.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(recentItems, key = { "recent_${it.videoId}" }) { track ->
                        HomeSongCard(
                            track = track,
                            onClick = { onTrackClick(track, recentItems) }
                        )
                    }
                }
            } else {
                PlaceholderRow()
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Section: Made for you
        item {
            Text(
                text = "Made for you",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextBone
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (forYouItems.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(forYouItems, key = { "foryou_${it.videoId}" }) { track ->
                        HomeSongCard(
                            track = track,
                            onClick = { onTrackClick(track, forYouItems) }
                        )
                    }
                }
            } else {
                PlaceholderRow()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun PlaylistGridTile(
    title: String,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(gradient)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = TextBone,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun HomeSongCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RaisedDark),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = TextBone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaceholderRow() {
    val gradients = listOf(
        Brush.linearGradient(listOf(Color(0xFF3D8BFF), Color(0xFF8B5CFF))),
        Brush.linearGradient(listOf(Color(0xFFFF3B5C), Color(0xFF8B5CFF))),
        Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF3D8BFF)))
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(3) { idx ->
            Column(modifier = Modifier.width(96.dp)) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(gradients[idx])
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Song title",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextBone
                )
                Text(
                    text = "Artist",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextMuted
                )
            }
        }
    }
}
