package com.skullbeat.app.ui.screens.nowplaying

import android.content.res.Configuration
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.skullbeat.app.data.storage.RepeatState
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.util.TimeUtils
import com.skullbeat.app.player.manager.LoopMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onAddToPlaylist: (Track) -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    val track = playbackState.currentTrack ?: return

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

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
            red = (topC.red * 0.45f + 0.02f * 0.55f),
            green = (topC.green * 0.45f + 0.027f * 0.55f),
            blue = (topC.blue * 0.45f + 0.059f * 0.55f),
            alpha = 1.0f
        )
        val solidMid = Color(
            red = (midC.red * 0.28f + 0.02f * 0.72f),
            green = (midC.green * 0.28f + 0.027f * 0.72f),
            blue = (midC.blue * 0.28f + 0.059f * 0.72f),
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = BgDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(horizontal = if (isLandscape) 16.dp else 24.dp)
        ) {
            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

            // Top Bar: Down chevron, PLAYING FROM PLAYLIST, 3-dot overflow menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = TextBone,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM PLAYLIST",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            color = TextMuted
                        )
                    )
                    Text(
                        text = playbackState.contextId ?: "Playlist name",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextBone
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = TextBone,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Artist", color = TextBone) },
                            onClick = {
                                showMenu = false
                                onDismiss()
                                onArtistClick(track.cleanArtist)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = TextBone) },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist(track)
                            }
                        )
                    }
                }
            }

            if (isLandscape) {
                // Landscape layout: Compact 180dp artwork on left, all controls visible without scrolling on right
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .shadow(elevation = 20.dp, shape = RoundedCornerShape(18.dp), spotColor = ElectricBlue)
                            .clip(RoundedCornerShape(18.dp))
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

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TrackInfoRow(
                            track = track,
                            isFavorite = isFavorite,
                            onFavoriteClick = { viewModel.toggleFavorite() },
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
                            loopMode = playbackState.loopMode,
                            remainingLoops = playbackState.remainingLoops,
                            onShuffleClick = { viewModel.toggleShuffle() },
                            onPrevClick = { viewModel.previous() },
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onNextClick = { viewModel.next() },
                            onSetLoopMode = { viewModel.setLoopMode(it) },
                            isCompact = true
                        )

                        BottomActionsRow(
                            onDownload = { onDownload(track) },
                            onOpenLyrics = { onOpenLyrics(track) },
                            onOpenQueue = { showQueueSheet = true }
                        )
                    }
                }
            } else {
                // Portrait layout: 310dp artwork, TrackInfo, Seekbar, Controls, Bottom Actions
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Large 310x310 Artwork with electric blue glow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                            .shadow(elevation = 28.dp, shape = RoundedCornerShape(20.dp), spotColor = ElectricBlue)
                            .clip(RoundedCornerShape(20.dp))
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Song Title, Artist, Heart Toggle
                    TrackInfoRow(
                        track = track,
                        isFavorite = isFavorite,
                        onFavoriteClick = { viewModel.toggleFavorite() },
                        onArtistClick = {
                            onDismiss()
                            onArtistClick(track.cleanArtist)
                        },
                        onTitleClick = { viewModel.togglePlayPause() }
                    )

                    // Sleek Seek Bar with 4dp track & current/total time
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

                    // Controls Row: Shuffle | Prev | Skull Play (76dp) | Next | Repeat
                    PlaybackButtonsRow(
                        isPlaying = playbackState.isPlaying,
                        isLoading = playbackState.isLoading,
                        isShuffle = playbackState.isShuffle,
                        loopMode = playbackState.loopMode,
                        remainingLoops = playbackState.remainingLoops,
                        onShuffleClick = { viewModel.toggleShuffle() },
                        onPrevClick = { viewModel.previous() },
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.next() },
                        onSetLoopMode = { viewModel.setLoopMode(it) },
                        isCompact = false
                    )

                    // Bottom Action Row: Download | Lyrics | Queue
                    BottomActionsRow(
                        onDownload = { onDownload(track) },
                        onOpenLyrics = { onOpenLyrics(track) },
                        onOpenQueue = { showQueueSheet = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    // Expandable Queue Bottom Sheet (Spotify-style Priority Queue)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Queue",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextBone
                    )
                    if (playbackState.userQueue.isNotEmpty()) {
                        Text(
                            text = "Clear Queue",
                            color = HeartRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.clearUserQueue() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Now Playing Section
                    item {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ElectricBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (track != null) {
                            TrackListItem(
                                track = track,
                                isCurrentTrack = true,
                                onClick = { showQueueSheet = false },
                                onArtistClick = { artist ->
                                    showQueueSheet = false
                                    onDismiss()
                                    onArtistClick(artist)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 2. Next in Queue (User Queued Items)
                    item {
                        Text(
                            text = "Next in Queue" + if (playbackState.userQueue.isNotEmpty()) " (${playbackState.userQueue.size})" else "",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextBone
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (playbackState.userQueue.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Slide any song left to right to add to queue",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        itemsIndexed(playbackState.userQueue, key = { idx, item -> "user_q_${idx}_${item.videoId}" }) { qIdx, qItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RaisedDark.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = qItem.thumbnailUrl,
                                    contentDescription = qItem.title,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qItem.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextBone,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = qItem.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.removeFromUserQueue(qIdx) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Next Up from Playlist (Follows shuffle or playlist order when user queue finishes)
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (playbackState.isShuffle) "Next Up (Shuffle Order)" else "Next from Playlist",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextBone
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val upcomingTracks = if (playbackState.isShuffle && playbackState.shuffleOrder.isNotEmpty()) {
                        val remainingIndices = playbackState.shuffleOrder.drop(playbackState.shufflePosition + 1)
                        remainingIndices.mapNotNull { if (it in playbackState.tracks.indices) playbackState.tracks[it] else null }
                    } else {
                        val nextIdx = playbackState.currentIndex + 1
                        if (nextIdx in playbackState.tracks.indices) playbackState.tracks.subList(nextIdx, playbackState.tracks.size)
                        else emptyList()
                    }

                    itemsIndexed(upcomingTracks, key = { idx, item -> "up_${idx}_${item.videoId}" }) { _, item ->
                        TrackListItem(
                            track = item,
                            isCurrentTrack = false,
                            onClick = {
                                viewModel.playQueueTrack(item)
                                showQueueSheet = false
                            },
                            onArtistClick = { artist ->
                                showQueueSheet = false
                                onDismiss()
                                onArtistClick(artist)
                            },
                            onSwipeToQueue = {
                                viewModel.addToQueue(item)
                                Toast.makeText(context, "Added to queue: ${item.title}", Toast.LENGTH_SHORT).show()
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
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextBone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onTitleClick() }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    color = TextMuted
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onArtistClick() }
            )
        }

        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) HeartRed else TextMuted,
                modifier = Modifier.size(26.dp)
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
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    color = TextMuted
                )
            )
            Text(
                text = TimeUtils.formatDuration(totalMs / 1000),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    color = TextMuted
                )
            )
        }
    }
}

@Composable
private fun PlaybackButtonsRow(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    isShuffle: Boolean,
    loopMode: LoopMode,
    remainingLoops: Int,
    onShuffleClick: () -> Unit,
    onPrevClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onSetLoopMode: (LoopMode) -> Unit,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onShuffleClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isShuffle) ElectricBlue else TextMuted,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(
            onClick = onPrevClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = TextBone,
                modifier = Modifier.size(34.dp)
            )
        }

        Box(
            modifier = Modifier.shadow(
                elevation = 24.dp,
                shape = CircleShape,
                spotColor = ElectricBlue
            )
        ) {
            SkullPlayButton(
                isPlaying = isPlaying,
                isLoading = isLoading,
                onClick = onPlayPauseClick,
                size = if (isCompact) 56.dp else 76.dp
            )
        }

        IconButton(
            onClick = onNextClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = TextBone,
                modifier = Modifier.size(34.dp)
            )
        }

        LoopControlButton(
            loopMode = loopMode,
            remainingLoops = remainingLoops,
            onSetLoopMode = onSetLoopMode,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun LoopControlButton(
    loopMode: LoopMode,
    remainingLoops: Int,
    onSetLoopMode: (LoopMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var tapCount by remember { mutableIntStateOf(0) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    IconButton(
        onClick = {
            if (loopMode != LoopMode.OFF) {
                // OFF by single touch
                debounceJob?.cancel()
                tapCount = 0
                onSetLoopMode(LoopMode.OFF)
                Toast.makeText(context, "Loop: Off", Toast.LENGTH_SHORT).show()
            } else {
                tapCount++
                debounceJob?.cancel()
                debounceJob = coroutineScope.launch {
                    delay(320)
                    val target = when (tapCount) {
                        1 -> LoopMode.INFINITE
                        2 -> LoopMode.ONE_TIME
                        else -> LoopMode.TWO_TIMES
                    }
                    onSetLoopMode(target)
                    val msg = when (target) {
                        LoopMode.INFINITE -> "Loop: On (Continuous)"
                        LoopMode.ONE_TIME -> "Loop: 1 time"
                        LoopMode.TWO_TIMES -> "Loop: 2 times"
                        else -> "Loop: Off"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    tapCount = 0
                }
            }
        },
        modifier = modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val isActive = loopMode != LoopMode.OFF
            val icon = when (loopMode) {
                LoopMode.ONE_TIME -> Icons.Default.RepeatOne
                else -> Icons.Default.Repeat
            }
            val tint = if (isActive) ElectricBlue else TextMuted

            Icon(
                imageVector = icon,
                contentDescription = "Loop",
                tint = tint,
                modifier = Modifier.size(24.dp)
            )

            // Small badge showing "1" or "2" for limited loop
            if (loopMode == LoopMode.ONE_TIME || loopMode == LoopMode.TWO_TIMES) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 0.dp, end = 0.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(ElectricBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (loopMode == LoopMode.ONE_TIME) "1" else "2",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionsRow(
    onDownload: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionTextButton(
            icon = Icons.Default.Download,
            label = "Download",
            onClick = onDownload
        )
        ActionTextButton(
            icon = Icons.Default.Lyrics,
            label = "Lyrics",
            onClick = onOpenLyrics
        )
        ActionTextButton(
            icon = Icons.Default.QueueMusic,
            label = "Queue",
            onClick = onOpenQueue
        )
    }
}

@Composable
private fun ActionTextButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = TextMuted
        )
    }
}
