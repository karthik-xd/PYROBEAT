package com.skullbeat.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Composable
fun LyricsDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    var lyrics by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(track.videoId) {
        isLoading = true
        lyrics = withContext(Dispatchers.IO) {
            try {
                // Free lyrics lookup from lrclib.net
                val client = OkHttpClient()
                val url = "https://lrclib.net/api/get?track_name=${java.net.URLEncoder.encode(track.title, "UTF-8")}&artist_name=${java.net.URLEncoder.encode(track.cleanArtist, "UTF-8")}"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        json.optString("plainLyrics").ifEmpty { json.optString("syncedLyrics") }
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Column {
                Text(
                    text = "Lyrics",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${track.title} - ${track.artist}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                    maxLines = 1
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = if (isLoading || lyrics == null) Alignment.Center else Alignment.TopStart
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = ElectricBlue)
                } else if (lyrics.isNullOrBlank()) {
                    Text(
                        text = "No lyrics found for this song.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                    )
                } else {
                    Text(
                        text = lyrics.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextBone, lineHeight = 26.sp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close", color = TextBone)
            }
        }
    )
}
