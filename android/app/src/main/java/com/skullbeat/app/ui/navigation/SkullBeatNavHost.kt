package com.skullbeat.app.ui.navigation

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.skullbeat.app.domain.models.Playlist
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.repository.MusicRepository
import com.skullbeat.app.download.DownloadWorker
import com.skullbeat.app.player.manager.PlayerManager
import com.skullbeat.app.ui.components.MiniPlayer
import com.skullbeat.app.ui.dialogs.AddToPlaylistDialog
import com.skullbeat.app.ui.dialogs.CreateEditPlaylistDialog
import com.skullbeat.app.ui.dialogs.ImportDialog
import com.skullbeat.app.ui.dialogs.ImportTab
import com.skullbeat.app.ui.dialogs.LyricsDialog
import com.skullbeat.app.ui.screens.artist.ArtistScreen
import com.skullbeat.app.ui.screens.home.HomeScreen
import com.skullbeat.app.ui.screens.library.LibraryScreen
import com.skullbeat.app.ui.screens.nowplaying.NowPlayingSheet
import com.skullbeat.app.ui.screens.playlist.PlaylistDetailScreen
import com.skullbeat.app.ui.screens.search.SearchScreen
import com.skullbeat.app.ui.screens.splash.SplashScreen
import kotlinx.coroutines.delay
import com.skullbeat.app.ui.theme.BgDark
import com.skullbeat.app.ui.theme.HighlightGradient
import com.skullbeat.app.ui.theme.RaisedDark
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SkullBeatNavHost(
    playerManager: PlayerManager,
    repository: MusicRepository,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playbackState by playerManager.playbackState.collectAsState()
    val playlists by repository.getPlaylists().collectAsState(initial = emptyList())

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var selectedTrackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var importUrlToOpen by remember { mutableStateOf<String?>(null) }
    var importTabToOpen by remember { mutableStateOf(ImportTab.LINK) }
    var selectedTrackForLyrics by remember { mutableStateOf<Track?>(null) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }
    var isSplashVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1300)
        isSplashVisible = false
    }

    fun enqueueDownload(track: Track) {
        val workData = Data.Builder()
            .putString(DownloadWorker.KEY_VIDEO_ID, track.videoId)
            .putString(DownloadWorker.KEY_TITLE, track.title)
            .putString(DownloadWorker.KEY_ARTIST, track.artist)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(context).enqueue(downloadWork)
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
        Scaffold(
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgDark)
                ) {
                    // Persistent Mini Player above bottom bar
                    if (playbackState.currentTrack != null) {
                        MiniPlayer(
                            currentTrack = playbackState.currentTrack,
                            isPlaying = playbackState.isPlaying,
                            isLoading = playbackState.isLoading,
                            currentPositionMs = playbackState.currentPositionMs,
                            durationMs = playbackState.durationMs,
                            primaryColor = playbackState.primaryColor,
                            secondaryColor = playbackState.secondaryColor,
                            onTogglePlayPause = { playerManager.togglePlayPause() },
                            onNext = { playerManager.next() },
                            onArtistClick = { artist -> navController.navigate(Screen.Artist.createRoute(artist)) },
                            onClick = { isNowPlayingOpen = true }
                        )
                    }

                    // Bottom Navigation Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(SurfaceDark)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomNavItem(
                            icon = Icons.Default.Home,
                            label = "Home",
                            isSelected = currentRoute == Screen.Home.route,
                            onClick = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        BottomNavItem(
                            icon = Icons.Default.Search,
                            label = "Search",
                            isSelected = currentRoute == Screen.Search.route,
                            onClick = {
                                navController.navigate(Screen.Search.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        BottomNavItem(
                            icon = Icons.Default.LibraryMusic,
                            label = "Library",
                            isSelected = currentRoute == Screen.Library.route,
                            onClick = {
                                navController.navigate(Screen.Library.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            containerColor = BgDark
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onTrackClick = { track, list -> playerManager.playTrack(track, list) },
                        onArtistClick = { artist -> navController.navigate(Screen.Artist.createRoute(artist)) },
                        onAddToPlaylist = { track -> selectedTrackForPlaylist = track },
                        onDownload = { track -> enqueueDownload(track) },
                        onPlaylistClick = { id -> navController.navigate(Screen.PlaylistDetail.createRoute(id)) }
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        onTrackClick = { track, list -> playerManager.playTrack(track, list) },
                        onArtistClick = { artist -> navController.navigate(Screen.Artist.createRoute(artist)) },
                        onAddToPlaylist = { track -> selectedTrackForPlaylist = track },
                        onDownload = { track -> enqueueDownload(track) },
                        onOpenImport = { url ->
                            importTabToOpen = ImportTab.LINK
                            importUrlToOpen = url
                        }
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        onTrackClick = { track, list -> playerManager.playTrack(track, list) },
                        onPlaylistClick = { id -> navController.navigate(Screen.PlaylistDetail.createRoute(id)) },
                        onCreatePlaylist = { showCreatePlaylistDialog = true },
                        onArtistClick = { artist -> navController.navigate(Screen.Artist.createRoute(artist)) },
                        onAddToPlaylist = { track -> selectedTrackForPlaylist = track },
                        onDownload = { track -> enqueueDownload(track) },
                        onOpenImport = { isCsv ->
                            importTabToOpen = if (isCsv) ImportTab.CSV else ImportTab.LINK
                            importUrlToOpen = ""
                        }
                    )
                }

                composable(Screen.Artist.route) {
                    ArtistScreen(
                        onBack = { navController.popBackStack() },
                        onAddToPlaylist = { track -> selectedTrackForPlaylist = track },
                        onDownload = { track -> enqueueDownload(track) },
                        onArtistClick = { artist -> navController.navigate(Screen.Artist.createRoute(artist)) }
                    )
                }

                composable(Screen.PlaylistDetail.route) {
                    PlaylistDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEditPlaylist = { pl ->
                            editingPlaylist = pl
                        },
                        onOpenImport = {
                            importTabToOpen = ImportTab.LINK
                            importUrlToOpen = ""
                        },
                        onArtistClick = { artist -> navController.navigate(Screen.Artist.createRoute(artist)) },
                        onAddToPlaylist = { track -> selectedTrackForPlaylist = track },
                        onDownload = { track -> enqueueDownload(track) }
                    )
                }
            }
        }

        // Full Screen Now Playing Sheet
        AnimatedVisibility(
            visible = isNowPlayingOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            NowPlayingSheet(
                onDismiss = { isNowPlayingOpen = false },
                onArtistClick = { artist -> navController.navigate(Screen.Artist.createRoute(artist)) },
                onDownload = { track -> enqueueDownload(track) },
                onOpenLyrics = { track -> selectedTrackForLyrics = track },
                onAddToPlaylist = { track -> selectedTrackForPlaylist = track }
            )
        }

        // Add to Playlist Dialog
        val trackForPlaylist = selectedTrackForPlaylist
        if (trackForPlaylist != null) {
            AddToPlaylistDialog(
                track = trackForPlaylist,
                playlists = playlists,
                onSelectPlaylist = { pl ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val added = repository.addTrackToPlaylist(pl.id, trackForPlaylist)
                            withContext(Dispatchers.Main) {
                                if (added) {
                                    Toast.makeText(context, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Already in ${pl.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("NavHost", "Failed to add track to playlist", e)
                        }
                    }
                    selectedTrackForPlaylist = null
                },
                onCreateNewPlaylist = {
                    showCreatePlaylistDialog = true
                },
                onDismiss = { selectedTrackForPlaylist = null }
            )
        }

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            val pendingTrack = selectedTrackForPlaylist
            CreateEditPlaylistDialog(
                onConfirm = { name, emoji, colorHex, coverUri, _ ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val newId = repository.createPlaylist(name, emoji, colorHex)
                            var finalCoverPath: String? = null
                            if (coverUri != null) {
                                finalCoverPath = com.skullbeat.app.domain.util.ImageUtils.processAndSavePlaylistCover(
                                    context = context,
                                    sourceUri = coverUri,
                                    playlistId = newId
                                )
                                if (finalCoverPath != null) {
                                    repository.updatePlaylistCover(newId, finalCoverPath)
                                }
                            }
                            if (pendingTrack != null) {
                                repository.addTrackToPlaylist(newId, pendingTrack)
                            }
                            withContext(Dispatchers.Main) {
                                val msg = if (pendingTrack != null) {
                                    "Created \"$name\" and added ${pendingTrack.title}"
                                } else {
                                    "Playlist \"$name\" created"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("NavHost", "Failed to create playlist", e)
                        }
                    }
                    selectedTrackForPlaylist = null
                    showCreatePlaylistDialog = false
                },
                onDismiss = {
                    selectedTrackForPlaylist = null
                    showCreatePlaylistDialog = false
                }
            )
        }

        // Edit Playlist Dialog
        val currentEditing = editingPlaylist
        if (currentEditing != null) {
            CreateEditPlaylistDialog(
                initialPlaylist = currentEditing,
                isEdit = true,
                onConfirm = { name, emoji, colorHex, coverUri, removeCover ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            var newCoverPath = currentEditing.coverPath
                            if (removeCover) {
                                com.skullbeat.app.domain.util.ImageUtils.deleteCoverFile(currentEditing.coverPath)
                                newCoverPath = null
                            } else if (coverUri != null) {
                                newCoverPath = com.skullbeat.app.domain.util.ImageUtils.processAndSavePlaylistCover(
                                    context = context,
                                    sourceUri = coverUri,
                                    playlistId = currentEditing.id,
                                    oldCoverPath = currentEditing.coverPath
                                )
                            }
                            repository.updatePlaylist(
                                playlistId = currentEditing.id,
                                name = name,
                                emoji = emoji,
                                colorHex = colorHex,
                                coverPath = newCoverPath
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Playlist updated", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("NavHost", "Failed to update playlist", e)
                        }
                    }
                    editingPlaylist = null
                },
                onDismiss = { editingPlaylist = null }
            )
        }

        // Import Dialog
        if (importUrlToOpen != null) {
            ImportDialog(
                initialUrl = importUrlToOpen.orEmpty(),
                initialTab = importTabToOpen,
                repository = repository,
                onImportSuccess = { playlistId ->
                    importUrlToOpen = null
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                },
                onDismiss = { importUrlToOpen = null }
            )
        }

        // Lyrics Dialog
        val lyricsTrack = selectedTrackForLyrics
        if (lyricsTrack != null) {
            LyricsDialog(
                track = lyricsTrack,
                onDismiss = { selectedTrackForLyrics = null }
            )
        }

        // App Launch Splash & Loading Screen
        AnimatedVisibility(
            visible = isSplashVisible,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(500))
        ) {
            SplashScreen()
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.background(HighlightGradient)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) TextBone else TextMuted,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) TextBone else TextMuted
                )
            )
        }
    }
}
