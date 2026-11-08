package com.skullbeat.app.data.network

import com.skullbeat.app.data.extractor.YouTubeExtractor
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.util.TrackMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyRawItem(
    val title: String,
    val artist: String
)

data class SpotifyImportResult(
    val playlistName: String,
    val tracks: List<Track>
)

@Singleton
class SpotifyScraper @Inject constructor(
    private val baseOkHttpClient: OkHttpClient,
    private val youTubeExtractor: YouTubeExtractor
) {
    private val client = baseOkHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun importSpotify(
        urlOrUri: String,
        onProgress: (matched: Int, total: Int) -> Unit = { _, _ -> }
    ): SpotifyImportResult = withContext(Dispatchers.IO) {
        val raw = urlOrUri.trim()
        if (raw.lowercase().contains("collection/tracks")) {
            throw IllegalArgumentException("Spotify Liked Songs is private. Make a public playlist from it and paste that link.")
        }

        val match = Regex("(playlist|album|track)[/:]([a-zA-Z0-9]+)").find(raw)
            ?: throw IllegalArgumentException("Invalid Spotify link format. Provide open.spotify.com/playlist/... or album/...")

        val entityType = match.groupValues[1]
        val entityId = match.groupValues[2]
        val embedUrl = "https://open.spotify.com/embed/$entityType/$entityId"

        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify embed HTTP error: ${response.code}")
            }
            response.body?.string().orEmpty()
        }

        var title = if (entityType == "playlist") "Spotify Playlist" else if (entityType == "album") "Spotify Album" else "Spotify Track"
        val rawItems = mutableListOf<SpotifyRawItem>()

        val nextDataMatch = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)
        if (nextDataMatch != null) {
            try {
                val json = JSONObject(nextDataMatch.groupValues[1])
                val entity = json.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")

                if (entity != null) {
                    val entityName = entity.optString("name").ifEmpty { entity.optString("title") }
                    if (entityName.isNotBlank()) {
                        title = entityName
                    }

                    if (entityType == "track") {
                        val trackArtists = entity.optJSONArray("artists")
                        val artistNames = mutableListOf<String>()
                        if (trackArtists != null) {
                            for (i in 0 until trackArtists.length()) {
                                artistNames.add(trackArtists.getJSONObject(i).optString("name"))
                            }
                        }
                        rawItems.add(SpotifyRawItem(title = title, artist = artistNames.joinToString(", ")))
                    } else {
                        val trackList = entity.optJSONArray("trackList")
                        if (trackList != null) {
                            for (i in 0 until trackList.length()) {
                                val tObj = trackList.getJSONObject(i)
                                val tTitle = tObj.optString("title")
                                val tArtist = tObj.optString("subtitle")
                                if (tTitle.isNotBlank()) {
                                    rawItems.add(SpotifyRawItem(title = tTitle, artist = tArtist))
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Fallback: regex search on trackList
        if (rawItems.isEmpty()) {
            val trackListMatch = Regex(""""trackList":\s*(\[.*?\])\s*,\s*"""", RegexOption.DOT_MATCHES_ALL).find(html)
            if (trackListMatch != null) {
                try {
                    val array = org.json.JSONArray(trackListMatch.groupValues[1])
                    for (i in 0 until array.length()) {
                        val tObj = array.getJSONObject(i)
                        val tTitle = tObj.optString("title")
                        val tArtist = tObj.optString("subtitle")
                        if (tTitle.isNotBlank()) {
                            rawItems.add(SpotifyRawItem(title = tTitle, artist = tArtist))
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (rawItems.isEmpty()) {
            throw IllegalStateException("No tracks found in Spotify link. Ensure the playlist is public.")
        }

        // Match first 80 items in parallel, max 8 coroutines
        val itemsToMatch = rawItems.take(80)
        val total = itemsToMatch.size
        val semaphore = Semaphore(8)
        var matchedCount = 0

        val deferreds = itemsToMatch.map { item ->
            async {
                semaphore.withPermit {
                    try {
                        val query = "${item.title} ${item.artist}".trim()
                        val hits = youTubeExtractor.searchSongs(query, limit = 5)
                        val hit = TrackMatcher.findBestMatch(item.title, item.artist, hits)
                        if (hit != null) {
                            synchronized(this@SpotifyScraper) {
                                matchedCount++
                                onProgress(matchedCount, total)
                            }
                        }
                        hit
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        val resolvedTracks = deferreds.awaitAll().filterNotNull()
        if (resolvedTracks.isEmpty()) {
            throw IllegalStateException("Could not match Spotify tracks to audio streams.")
        }

        SpotifyImportResult(
            playlistName = title,
            tracks = resolvedTracks
        )
    }
}
