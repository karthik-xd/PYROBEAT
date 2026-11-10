package com.skullbeat.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.skullbeat.app.domain.models.Playlist
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.util.FilterCriteria
import com.skullbeat.app.domain.util.TimeUtils
import com.skullbeat.app.ui.theme.CyanAccent
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.HighlightGradient
import com.skullbeat.app.ui.theme.RaisedDark
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onOpenImport: (isCsv: Boolean) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val filterCriteria by viewModel.filterCriteria.collectAsState()
    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    var showAddMenuSheet by remember { mutableStateOf(false) }
    var selectedFilterField by remember { mutableStateOf<String?>("Artist") }
    var durationRange by remember { mutableStateOf(120f..360f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Screen Title & Add Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextBone
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Add Feature Button (+)
                IconButton(
                    onClick = { showAddMenuSheet = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(RaisedDark)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Feature",
                        tint = ElectricBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (selectedTab == LibraryTab.HISTORY) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RaisedDark)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs: All | Liked | Playlists | History
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                LibraryTab.ALL to "All",
                LibraryTab.LIKED to "Liked",
                LibraryTab.PLAYLISTS to "Playlists",
                LibraryTab.HISTORY to "History"
            )

            tabs.forEach { (tab, label) ->
                val isSelected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (isSelected) Modifier.background(HighlightGradient)
                            else Modifier.background(RaisedDark)
                        )
                        .clickable { viewModel.selectTab(tab) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) TextBone else TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == LibraryTab.PLAYLISTS) {
            // Playlists Tab
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No playlists yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextBone
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+' to create or import a playlist",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(playlists, key = { "pl_${it.id}" }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) }
                        )
                    }
                }
            }
        } else {
            // Track List with Filter UI matching Image 3
            // 1. Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = filterCriteria.query,
                    onValueChange = { viewModel.updateFilterCriteria(filterCriteria.copy(query = it)) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = TextBone
                    ),
                    cursorBrush = SolidColor(ElectricBlue),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (filterCriteria.query.isEmpty()) {
                            Text(
                                text = "Filter by title, artist or album",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    color = TextMuted
                                )
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Filter Chips: Artist | Title | Album
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("Artist", "Title", "Album").forEach { chip ->
                    val isSelected = selectedFilterField == chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) ElectricBlue.copy(alpha = 0.20f)
                                else SurfaceDark
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ElectricBlue else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selectedFilterField = if (isSelected) null else chip
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = chip,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) ElectricBlue else TextBone
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Duration Range Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextMuted
                )
                Text(
                    text = "${TimeUtils.formatDuration(durationRange.start.toLong())} – ${TimeUtils.formatDuration(durationRange.endInclusive.toLong())}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextMuted
                )
            }

            RangeSlider(
                value = durationRange,
                onValueChange = { durationRange = it },
                valueRange = 0f..600f,
                colors = SliderDefaults.colors(
                    thumbColor = TextBone,
                    activeTrackColor = ElectricBlue,
                    inactiveTrackColor = RaisedDark
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Count & Export Links Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredTracks.size} tracks",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = TextMuted
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Export ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                        color = TextMuted
                    )
                    Text(
                        text = "M3U",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        ),
                        modifier = Modifier.clickable { viewModel.exportFilteredM3u() }
                    )
                    Text(text = " • ", color = TextMuted)
                    Text(
                        text = "CSV",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        ),
                        modifier = Modifier.clickable { viewModel.exportFilteredCsv() }
                    )
                    Text(text = " • ", color = TextMuted)
                    Text(
                        text = "JSON",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        ),
                        modifier = Modifier.clickable { viewModel.exportFilteredJson() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Track List
            if (filteredTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks match your filter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredTracks, key = { "lib_${it.videoId}" }) { track ->
                        LibraryTrackRow(
                            track = track,
                            isCurrentTrack = playbackState.currentTrack?.videoId == track.videoId,
                            onClick = { onTrackClick(track, filteredTracks) },
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }
    }

    // Add Feature Bottom Sheet
    if (showAddMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddMenuSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Add to SkullBeat",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextBone
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                AddMenuOption(
                    icon = Icons.Default.PlaylistAdd,
                    title = "New Playlist",
                    subtitle = "Create custom playlist with emoji & color",
                    onClick = {
                        showAddMenuSheet = false
                        onCreatePlaylist()
                    }
                )

                AddMenuOption(
                    icon = Icons.Default.Link,
                    title = "Import Link",
                    subtitle = "Stream Spotify or YouTube playlist directly",
                    onClick = {
                        showAddMenuSheet = false
                        onOpenImport(false)
                    }
                )

                AddMenuOption(
                    icon = Icons.Default.UploadFile,
                    title = "Upload Song CSV",
                    subtitle = "Import mapped songs from CSV file",
                    onClick = {
                        showAddMenuSheet = false
                        onOpenImport(true)
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun LibraryTrackRow(
    track: Track,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrentTrack) RaisedDark else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RaisedDark)
                .clickable { onArtistClick(track.cleanArtist) },
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentTrack) ElectricBlue else TextBone
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.artist} • ${track.album.ifBlank { "Single" }}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = TextMuted
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = track.durationFormatted.ifBlank { "3:42" },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                color = TextMuted
            )
        )
    }
}

@Composable
private fun AddMenuOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(RaisedDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricBlue,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextBone
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val bgColor = remember(playlist.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(playlist.colorHex))
        } catch (_: Exception) {
            Color(0xFF3D8BFF)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(text = playlist.emoji, fontSize = 40.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = TextBone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Playlist",
            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
        )
    }
}
