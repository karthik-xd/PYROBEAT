package com.skullbeat.app.ui.dialogs

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skullbeat.app.domain.repository.MusicRepository
import com.skullbeat.app.domain.util.CsvTrackParser
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.HeartRed
import com.skullbeat.app.ui.theme.RaisedDark
import com.skullbeat.app.ui.theme.SuccessGreen
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted
import com.skullbeat.app.ui.theme.WarningYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ImportTab {
    LINK, CSV
}

@Composable
fun ImportDialog(
    initialUrl: String = "",
    initialTab: ImportTab = ImportTab.LINK,
    repository: MusicRepository,
    onImportSuccess: (playlistId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(initialTab) }
    var url by remember { mutableStateOf(initialUrl) }
    var isImporting by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // CSV Specific State
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var parsedCsvSongs by remember { mutableStateOf<List<CsvTrackParser.CsvRow>>(emptyList()) }
    var csvPlaylistName by remember {
        mutableStateOf("CSV Import ${SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date())}")
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Query display name
                var name = "songs.csv"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIdx)
                    }
                }
                selectedFileName = name

                // Read and parse
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val parsed = CsvTrackParser.parse(stream)
                    if (parsed.isEmpty()) {
                        errorMessage = "No song titles detected. Check CSV headers or content."
                        parsedCsvSongs = emptyList()
                    } else {
                        parsedCsvSongs = parsed
                        errorMessage = null
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Failed to read CSV: ${e.localizedMessage}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        containerColor = SurfaceDark,
        title = {
            Column {
                Text(
                    text = "Import & Map Music",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Tab Switcher (Link vs CSV)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RaisedDark)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeTab == ImportTab.LINK) ElectricBlue else RaisedDark)
                            .clickable(enabled = !isImporting) { activeTab = ImportTab.LINK }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Link",
                                tint = if (activeTab == ImportTab.LINK) TextBone else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Playlist Link",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeTab == ImportTab.LINK) TextBone else TextMuted
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeTab == ImportTab.CSV) ElectricBlue else RaisedDark)
                            .clickable(enabled = !isImporting) { activeTab = ImportTab.CSV }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "CSV",
                                tint = if (activeTab == ImportTab.CSV) TextBone else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upload CSV",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeTab == ImportTab.CSV) TextBone else TextMuted
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (activeTab == ImportTab.LINK) {
                    Text(
                        text = "Paste a Spotify or YouTube playlist/album link to match audio streams with similarity checking.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RaisedDark)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isImporting,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextBone),
                            cursorBrush = SolidColor(ElectricBlue),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (url.isEmpty()) {
                                    Text(
                                        text = "https://open.spotify.com/... or youtube.com/...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                } else {
                    // CSV Import UI
                    Text(
                        text = "Upload a CSV with song names (and optional artists). Each track will be mapped by title similarity and added directly to your library.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = !isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = RaisedDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Choose CSV",
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedFileName != null) "Change CSV File" else "Choose CSV File",
                            color = TextBone,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (selectedFileName != null && parsedCsvSongs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(RaisedDark)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Valid",
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${parsedCsvSongs.size} songs ready from $selectedFileName",
                                fontSize = 12.sp,
                                color = TextBone,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Playlist Name",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(RaisedDark)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            BasicTextField(
                                value = csvPlaylistName,
                                onValueChange = { csvPlaylistName = it },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isImporting,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextBone),
                                cursorBrush = SolidColor(ElectricBlue),
                                singleLine = true
                            )
                        }
                    }
                }

                // Error Notice
                AnimatedVisibility(visible = errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(RaisedDark)
                            .padding(10.dp),
                            verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = if (errorMessage?.contains("Liked Songs") == true) WarningYellow else HeartRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, color = TextBone)
                        )
                    }
                }

                // Progress Bar
                AnimatedVisibility(visible = isImporting) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = progressFraction,
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = ElectricBlue,
                            trackColor = RaisedDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = progressStatus,
                            style = MaterialTheme.typography.labelSmall.copy(color = ElectricBlue)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (activeTab == ImportTab.LINK) {
                        val cleanUrl = url.trim()
                        if (cleanUrl.isBlank()) return@Button

                        if (cleanUrl.lowercase().contains("collection/tracks")) {
                            errorMessage = "Spotify Liked Songs is private. Make a public playlist from it and paste that link."
                            return@Button
                        }

                        isImporting = true
                        errorMessage = null
                        progressStatus = "Connecting to playlist..."
                        progressFraction = 0.1f

                        scope.launch {
                            try {
                                if (cleanUrl.contains("spotify.com") || cleanUrl.startsWith("spotify:")) {
                                    val result = repository.importSpotify(cleanUrl) { matched, total ->
                                        scope.launch(Dispatchers.Main) {
                                            progressStatus = "Matched $matched / $total songs with title similarity..."
                                            progressFraction = (matched.toFloat() / total.toFloat()).coerceIn(0.1f, 0.95f)
                                        }
                                    }

                                    val playlistId = repository.createPlaylist(
                                        name = result.playlistName,
                                        emoji = "🎧",
                                        colorHex = "#3D8BFF"
                                    )
                                    for (track in result.tracks) {
                                        repository.addTrackToPlaylist(playlistId, track)
                                    }

                                    withContext(Dispatchers.Main) {
                                        isImporting = false
                                        onImportSuccess(playlistId)
                                    }
                                } else {
                                    progressStatus = "Parsing YouTube playlist..."
                                    val (title, tracks) = repository.importYouTubePlaylist(cleanUrl)
                                    val playlistId = repository.createPlaylist(
                                        name = title,
                                        emoji = "▶️",
                                        colorHex = "#FF3B5C"
                                    )
                                    for (track in tracks) {
                                        repository.addTrackToPlaylist(playlistId, track)
                                    }

                                    withContext(Dispatchers.Main) {
                                        isImporting = false
                                        onImportSuccess(playlistId)
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isImporting = false
                                    errorMessage = e.localizedMessage ?: "Failed to import playlist"
                                }
                            }
                        }
                    } else {
                        // CSV Import Execution
                        if (parsedCsvSongs.isEmpty()) {
                            errorMessage = "Please choose a CSV file first"
                            return@Button
                        }

                        isImporting = true
                        errorMessage = null
                        progressStatus = "Mapping songs from CSV..."
                        progressFraction = 0.05f

                        scope.launch {
                            try {
                                val songPairs = parsedCsvSongs.map { it.title to it.artist }
                                val (title, matchedTracks) = repository.importCsvSongs(songPairs) { matched, total, currentSong ->
                                    scope.launch(Dispatchers.Main) {
                                        progressStatus = "Mapped $matched / $total: $currentSong"
                                        progressFraction = (matched.toFloat() / total.toFloat()).coerceIn(0.05f, 0.95f)
                                    }
                                }

                                if (matchedTracks.isEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        isImporting = false
                                        errorMessage = "Could not find matches for songs in CSV."
                                    }
                                    return@launch
                                }

                                val playlistName = csvPlaylistName.ifBlank { title }
                                val playlistId = repository.createPlaylist(
                                    name = playlistName,
                                    emoji = "📁",
                                    colorHex = "#00E5FF"
                                )
                                for (track in matchedTracks) {
                                    repository.addTrackToPlaylist(playlistId, track)
                                }

                                withContext(Dispatchers.Main) {
                                    isImporting = false
                                    onImportSuccess(playlistId)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isImporting = false
                                    errorMessage = e.localizedMessage ?: "Failed to process CSV"
                                }
                            }
                        }
                    }
                },
                enabled = !isImporting && if (activeTab == ImportTab.LINK) url.isNotBlank() else parsedCsvSongs.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(color = TextBone, modifier = Modifier.size(16.dp))
                } else {
                    Icon(
                        imageVector = if (activeTab == ImportTab.LINK) Icons.Default.FileDownload else Icons.Default.UploadFile,
                        contentDescription = "Import",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeTab == ImportTab.LINK) "Start Import" else "Map & Add to Library",
                        color = TextBone,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            if (!isImporting) {
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel", color = TextMuted)
                }
            }
        }
    )
}
