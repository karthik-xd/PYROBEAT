package com.skullbeat.app.ui.screens.artist

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.ui.components.TrackListItem
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.RaisedDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted

@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onArtistClick: (String) -> Unit = {},
    viewModel: ArtistViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val context = LocalContext.current

    val favIds = remember(favorites) { favorites.map { it.videoId }.toSet() }
    val avatarUrl = songs.firstOrNull()?.thumbnailUrl.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RaisedDark)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = TextBone,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Artist Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = viewModel.artistName,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(RaisedDark),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = viewModel.artistName,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${songs.size} tracks",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.playAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play All",
                        tint = TextBone,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Play All", color = TextBone, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(36.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(songs, key = { "artist_${it.videoId}" }) { track ->
                    TrackListItem(
                        track = track,
                        isCurrentTrack = playbackState.currentTrack?.videoId == track.videoId,
                        isFavorite = favIds.contains(track.videoId),
                        onClick = { viewModel.playTrack(track) },
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onDownload = { onDownload(track) },
                        onArtistClick = onArtistClick,
                        onSwipeToQueue = {
                            viewModel.addToQueue(track)
                            Toast.makeText(context, "Added to queue: ${track.title}", Toast.LENGTH_SHORT).show()
                        },
                        onSwipeAction = { action ->
                            when (action) {
                                com.skullbeat.app.ui.components.SwipeAction.ADD_TO_QUEUE -> {
                                    viewModel.addToQueue(track)
                                    Toast.makeText(context, "Added to queue: ${track.title}", Toast.LENGTH_SHORT).show()
                                }
                                com.skullbeat.app.ui.components.SwipeAction.PLAY_NEXT -> {
                                    viewModel.addToQueue(track)
                                    Toast.makeText(context, "Playing next: ${track.title}", Toast.LENGTH_SHORT).show()
                                }
                                com.skullbeat.app.ui.components.SwipeAction.ADD_TO_PLAYLIST -> {
                                    onAddToPlaylist(track)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
