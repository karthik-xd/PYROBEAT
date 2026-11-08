package com.skullbeat.app.data.extractor

import com.skullbeat.app.data.network.OkHttpDownloader
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val streamCache: StreamCache
) {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            val downloader = OkHttpDownloader(okHttpClient)
            NewPipe.init(downloader, Localization.DEFAULT, ContentCountry.DEFAULT)
        }
    }

    suspend fun searchSongs(query: String, limit: Int = 24): List<Track> = withContext(Dispatchers.IO) {
        ensureInitialized()
        if (query.isBlank()) return@withContext emptyList()

        try {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
            searchExtractor.fetchPage()
            val initialPage = searchExtractor.initialPage
            val items = initialPage.items.filterIsInstance<StreamInfoItem>()

            items.take(limit).mapNotNull { item ->
                val videoId = extractVideoId(item.url) ?: return@mapNotNull null
                val durationSec = if (item.duration > 0) item.duration else 0L
                val rawThumb = item.thumbnails.lastOrNull()?.url
                    ?: item.thumbnails.firstOrNull()?.url
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                val upscaledThumb = upscaleThumbnail(rawThumb)

                Track(
                    videoId = videoId,
                    title = item.name.orEmpty(),
                    artist = item.uploaderName.orEmpty(),
                    album = "Single",
                    durationSec = durationSec,
                    durationFormatted = TimeUtils.formatDuration(durationSec),
                    thumbnailUrl = upscaledThumb
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    suspend fun getArtistSongs(artist: String, currentVideoId: String? = null, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val cleanArtist = artist.split(Regex("(?i)\\s*(?:,|&|\\bfeat\\.?|\\bft\\.?)\\s*")).firstOrNull()?.trim().orEmpty()
        if (cleanArtist.isBlank()) return@withContext emptyList()

        val results = searchSongs("$cleanArtist songs", limit = limit + 5)
        results.filter { it.videoId != currentVideoId }.take(limit)
    }

    suspend fun resolveStreamUrl(videoId: String, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        ensureInitialized()
        if (!forceRefresh) {
            streamCache.get(videoId)?.let { return@withContext it }
        }

        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val streamInfo = StreamInfo.getInfo(watchUrl)
        val audioStreams = streamInfo.audioStreams

        if (audioStreams.isNullOrEmpty()) {
            throw IllegalStateException("No audio streams available for $videoId")
        }

        // Sort: prefer M4A/AAC, highest bitrate first
        val bestStream: AudioStream = audioStreams.sortedWith(
            compareByDescending<AudioStream> { isM4A(it) }
                .thenByDescending { it.averageBitrate }
        ).first()

        val streamUrl = bestStream.content
        streamCache.put(videoId, streamUrl)
        streamUrl
    }

    fun invalidateCache(videoId: String) {
        streamCache.invalidate(videoId)
    }

    suspend fun getYouTubePlaylist(urlOrId: String): Pair<String, List<Track>> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val playlistUrl = if (urlOrId.startsWith("http://") || urlOrId.startsWith("https://")) {
            urlOrId
        } else {
            "https://www.youtube.com/playlist?list=$urlOrId"
        }

        val playlistInfo = PlaylistInfo.getInfo(playlistUrl)
        val playlistTitle = playlistInfo.name.orEmpty().ifEmpty { "YouTube Playlist" }

        val tracks = mutableListOf<Track>()
        val initialItems = playlistInfo.relatedItems

        for (item in initialItems) {
            val videoId = extractVideoId(item.url) ?: continue
            val durationSec = if (item.duration > 0) item.duration else 0L
            val thumb = item.thumbnails.lastOrNull()?.url
                ?: item.thumbnails.firstOrNull()?.url
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            tracks.add(
                Track(
                    videoId = videoId,
                    title = item.name.orEmpty(),
                    artist = item.uploaderName.orEmpty(),
                    album = playlistTitle,
                    durationSec = durationSec,
                    durationFormatted = TimeUtils.formatDuration(durationSec),
                    thumbnailUrl = upscaleThumbnail(thumb)
                )
            )
        }

        var nextPage = playlistInfo.nextPage
        while (nextPage != null && tracks.size < 200) {
            try {
                val pageResult = PlaylistInfo.getMoreItems(playlistInfo.service, playlistInfo.url, nextPage)
                for (item in pageResult.items) {
                    val videoId = extractVideoId(item.url) ?: continue
                    val durationSec = if (item.duration > 0) item.duration else 0L
                    val thumb = item.thumbnails.lastOrNull()?.url
                        ?: item.thumbnails.firstOrNull()?.url
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    tracks.add(
                        Track(
                            videoId = videoId,
                            title = item.name.orEmpty(),
                            artist = item.uploaderName.orEmpty(),
                            album = playlistTitle,
                            durationSec = durationSec,
                            durationFormatted = TimeUtils.formatDuration(durationSec),
                            thumbnailUrl = upscaleThumbnail(thumb)
                        )
                    )
                }
                nextPage = pageResult.nextPage
            } catch (_: Exception) {
                break
            }
        }

        Pair(playlistTitle, tracks)
    }

    private fun isM4A(stream: AudioStream): Boolean {
        val formatName = stream.format?.name.orEmpty()
        val codec = stream.codec.orEmpty()
        return formatName.contains("m4a", ignoreCase = true) || codec.contains("mp4a", ignoreCase = true)
    }

    private fun extractVideoId(url: String): String? {
        val regex = Regex("(?:v=|youtu\\.be/|shorts/|^)([a-zA-Z0-9_-]{11})")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun upscaleThumbnail(url: String): String {
        return if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            url.replace(Regex("=w\\d+-h\\d+.*"), "=w500-h500")
                .replace(Regex("=s\\d+.*"), "=s500")
        } else if (url.contains("ytimg.com")) {
            url.replace("default.jpg", "hqdefault.jpg")
        } else {
            url
        }
    }
}
