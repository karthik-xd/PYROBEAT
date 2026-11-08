package com.skullbeat.app.ui.screens.nowplaying

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.skullbeat.app.data.storage.RepeatState
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.util.TimeUtils
import com.skullbeat.app.ui.components.SkullPlayButton
import com.skullbeat.app.ui.components.TrackListItem
import com.skullbeat.app.ui.theme.BgDark
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.HeartRed
import com.skullbeat.app.ui.theme.RaisedDark
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    onDismiss: () -> Unit,
    onArtistClick: (String) -> Unit,
    onDownload: (Track) -> Unit,
    onOpenLyrics: (Track) -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    val track = playbackState.currentTrack ?: return

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var showQueueSheet by remember { mutableStateOf(false) }

    val currentMs = if (isSeeking) seekPosition.toLong() else playbackState.currentPositionMs
    val totalMs = if (playbackState.durationMs > 0) playbackState.durationMs else (track.durationSec * 1000L)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val pColor = playbackState.primaryColor
    val sColor = playbackState.secondaryColor
    val gradientBrush = if (pColor != null && sColor != null) {
        val topC = Color(pColor)
        val midC = Color(sColor)
        val solidTop = Color(
            red = (topC.red * 0.45f + 0.055f * 0.55f),
            green = (topC.green * 0.45f + 0.055f * 0.55f),
            blue = (topC.blue * 0.45f + 0.078f * 0.55f),
            alpha = 1.0f
        )
        val solidMid = Color(
            red = (midC.red * 0.28f + 0.055f * 0.72f),
            green = (midC.green * 0.28f + 0.055f * 0.72f),
            blue = (midC.blue * 0.28f + 0.078f * 0.72f),
            alpha = 1.0f
        )
        Brush.verticalGradient(
            colors = listOf(
                solidTop,
                solidMid,
                BgDark
            )
        )
    } else {
        Brush.verticalGradient(listOf(BgDark, BgDark))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .background(gradientBrush)
            .padding(horizontal = if (isLandscape) 16.dp else 24.dp)
    ) {
        Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

        // Top Row: Dismiss down chevron & Queue icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(if (isLandscape) 34.dp else 40.dp)
                    .clip(CircleShape)
                    .background(RaisedDark)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = TextBone,
                    modifier = Modifier.size(if (isLandscape) 22.dp else 26.dp)
                )
            }

            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 13.sp, letterSpacing = 2.sp),
                color = TextMuted
            )

            IconButton(
                onClick = { showQueueSheet = true },
                modifier = Modifier
                    .size(if (isLandscape) 34.dp else 40.dp)
                    .clip(CircleShape)
                    .background(RaisedDark)
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = TextBone,
                    modifier = Modifier.size(if (isLandscape) 18.dp else 20.dp)
                )
            }
        }

        if (isLandscape) {
            // Landscape layout: Album Art on Left, Full Controls on Right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art fitting vertical screen
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp), spotColor = ElectricBlue)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .clickable {
                            onDismiss()
                            onArtistClick(track.cleanArtist)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Scrollable Controls Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    TrackInfoRow(
                        track = track,
                        isFavorite = isFavorite,
                        onFavoriteClick = { viewModel.toggleFavorite() },
                        onLyricsClick = { onOpenLyrics(track) },
                        onDownloadClick = { onDownload(track) },
                        onArtistClick = {
                            onDismiss()
                            onArtistClick(track.cleanArtist)
                        },
                        onTitleClick = { viewModel.togglePlayPause() }
                    )

                    SeekBarControl(
                        currentMs = currentMs,
                        totalMs = totalMs,
                        onSeekChange = {
                            isSeeking = true
                            seekPosition = it
                        },
                        onSeekFinished = {
                            viewModel.seekTo(seekPosition.toLong())
                            isSeeking = false
                        }
                    )

                    PlaybackButtonsRow(
                        isPlaying = playbackState.isPlaying,
                        isLoading = playbackState.isLoading,
                        isShuffle = playbackState.isShuffle,
                        repeatMode = playbackState.repeatMode,
                        onShuffleClick = { viewModel.toggleShuffle() },
                        onPrevClick = { viewModel.previous() },
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.next() },
                        onRepeatClick = { viewModel.toggleRepeat() },
                        isCompact = true
                    )

                    VolumeControlRow(
                        volume = playbackState.volume,
                        onVolumeChange = { viewModel.setVolume(it) }
                    )
                }
            }
        } else {
            // Portrait layout: Artwork on top, Controls below, scrollable if height constrained
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Large Artwork with Glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(elevation = 20.dp, shape = RoundedCornerShape(16.dp), spotColor = ElectricBlue)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .clickable {
                            onDismiss()
                            onArtistClick(track.cleanArtist)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                TrackInfoRow(
                    track = track,
                    isFavorite = isFavorite,
                    onFavoriteClick = { viewModel.toggleFavorite() },
                    onLyricsClick = { onOpenLyrics(track) },
                    onDownloadClick = { onDownload(track) },
                    onArtistClick = {
                        onDismiss()
                        onArtistClick(track.cleanArtist)
                    },
                    onTitleClick = { viewModel.togglePlayPause() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SeekBarControl(
                    currentMs = currentMs,
                    totalMs = totalMs,
                    onSeekChange = {
                        isSeeking = true
                        seekPosition = it
                    },
                    onSeekFinished = {
                        viewModel.seekTo(seekPosition.toLong())
                        isSeeking = false
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                PlaybackButtonsRow(
                    isPlaying = playbackState.isPlaying,
                    isLoading = playbackState.isLoading,
                    isShuffle = playbackState.isShuffle,
                    repeatMode = playbackState.repeatMode,
                    onShuffleClick = { viewModel.toggleShuffle() },
                    onPrevClick = { viewModel.previous() },
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onNextClick = { viewModel.next() },
                    onRepeatClick = { viewModel.toggleRepeat() },
                    isCompact = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                VolumeControlRow(
                    volume = playbackState.volume,
                    onVolumeChange = { viewModel.setVolume(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Expandable Queue Bottom Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Playing Queue (${playbackState.tracks.size} tracks)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp)
                ) {
                    itemsIndexed(playbackState.tracks, key = { idx, item -> "queue_${idx}_${item.videoId}" }) { _, item ->
                        TrackListItem(
                            track = item,
                            isCurrentTrack = item.videoId == track.videoId,
                            onClick = {
                                viewModel.playQueueTrack(item)
                                showQueueSheet = false
                            },
                            onArtistClick = { artist ->
                                showQueueSheet = false
                                onDismiss()
                                onArtistClick(artist)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackInfoRow(
    track: Track,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onArtistClick: () -> Unit,
    onTitleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onTitleClick() }
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, color = TextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onArtistClick() }
            )
        }

        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) HeartRed else TextMuted,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = onLyricsClick) {
            Icon(
                imageVector = Icons.Default.Lyrics,
                contentDescription = "Lyrics",
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(onClick = onDownloadClick) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SeekBarControl(
    currentMs: Long,
    totalMs: Long,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = currentMs.toFloat(),
            onValueChange = onSeekChange,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..maxOf(totalMs.toFloat(), 1f),
            colors = SliderDefaults.colors(
                thumbColor = ElectricBlue,
                activeTrackColor = ElectricBlue,
                inactiveTrackColor = RaisedDark
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = TimeUtils.formatDuration(currentMs / 1000),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = TimeUtils.formatDuration(totalMs / 1000),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun PlaybackButtonsRow(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    isShuffle: Boolean,
    repeatMode: RepeatState,
    onShuffleClick: () -> Unit,
    onPrevClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onRepeatClick: () -> Unit,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onShuffleClick) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isShuffle) ElectricBlue else TextMuted,
                modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
            )
        }

        IconButton(onClick = onPrevClick) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = TextBone,
                modifier = Modifier.size(if (isCompact) 28.dp else 34.dp)
            )
        }

        SkullPlayButton(
            isPlaying = isPlaying,
            isLoading = isLoading,
            onClick = onPlayPauseClick,
            size = if (isCompact) 50.dp else 64.dp
        )

        IconButton(onClick = onNextClick) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = TextBone,
                modifier = Modifier.size(if (isCompact) 28.dp else 34.dp)
            )
        }

        IconButton(onClick = onRepeatClick) {
            Icon(
                imageVector = when (repeatMode) {
                    RepeatState.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = "Repeat",
                tint = when (repeatMode) {
                    RepeatState.OFF -> TextMuted
                    else -> ElectricBlue
                },
                modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
            )
        }
    }
}

@Composable
private fun VolumeControlRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.VolumeDown,
            contentDescription = "Volume Down",
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = TextBone,
                activeTrackColor = ElectricBlue,
                inactiveTrackColor = RaisedDark
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = "Volume Up",
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
