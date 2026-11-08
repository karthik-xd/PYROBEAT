package com.skullbeat.app.player.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.skullbeat.app.data.extractor.YouTubeExtractor
import com.skullbeat.app.data.storage.PlayerSettings
import com.skullbeat.app.data.storage.RepeatState
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.repository.MusicRepository
import com.skullbeat.app.player.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val extractor: YouTubeExtractor,
    private val repository: MusicRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var retryCount = 0
    private var lastSavedSec = 0L

    val exoPlayer: ExoPlayer by lazy {
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val resolvingFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(context, httpDataSourceFactory),
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    if (uri.scheme == "skullbeat") {
                        val videoId = uri.getQueryParameter("id") ?: uri.authority.orEmpty()
                        val streamUrl = runBlocking(Dispatchers.IO) {
                            extractor.resolveStreamUrl(videoId)
                        }
                        return dataSpec.buildUpon().setUri(Uri.parse(streamUrl)).build()
                    }
                    return dataSpec
                }
            }
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(resolvingFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                addListener(playerListener)
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update {
                it.copy(
                    isPlaying = isPlaying,
                    isLoading = if (isPlaying) false else it.isLoading
                )
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    _playbackState.update { it.copy(isLoading = true) }
                }
                Player.STATE_READY -> {
                    retryCount = 0
                    val duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
                    _playbackState.update {
                        it.copy(
                            durationMs = duration,
                            isLoading = false,
                            isPlaying = exoPlayer.playWhenReady,
                            error = null
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    _playbackState.update { it.copy(isLoading = false) }
                    handleTrackEnded()
                }
                Player.STATE_IDLE -> {
                    _playbackState.update { it.copy(isLoading = false) }
                }
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val currentTrack = _playbackState.value.currentTrack
            if (currentTrack != null && retryCount < 1) {
                retryCount++
                scope.launch {
                    extractor.invalidateCache(currentTrack.videoId)
                    playTrack(currentTrack, _playbackState.value.tracks, _playbackState.value.contextType, _playbackState.value.contextId)
                }
            } else {
                _playbackState.update { it.copy(error = error.localizedMessage ?: "Playback error") }
                next()
            }
        }
    }

    init {
        startProgressTicker()
        restoreSettingsAndLastTrack()
    }

    private fun startProgressTicker() {
        scope.launch {
            while (true) {
                delay(250)
                try {
                    if (exoPlayer.isPlaying) {
                        val pos = exoPlayer.currentPosition
                        val dur = if (exoPlayer.duration > 0) exoPlayer.duration else _playbackState.value.durationMs
                        _playbackState.update {
                            it.copy(currentPositionMs = pos, durationMs = dur)
                        }

                        val currentSec = pos / 1000
                        if (currentSec - lastSavedSec >= 5) {
                            lastSavedSec = currentSec
                            val track = _playbackState.value.currentTrack
                            if (track != null) {
                                repository.savePlaybackState(track.videoId, pos)
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Ignore transient player state errors during transitions
                }
            }
        }
    }

    private fun restoreSettingsAndLastTrack() {
        scope.launch {
            val settings = repository.settingsFlow.firstOrNull() ?: return@launch
            _playbackState.update {
                it.copy(
                    isShuffle = settings.shuffle,
                    repeatMode = settings.repeatMode,
                    volume = settings.volume
                )
            }
            exoPlayer.volume = settings.volume

            val savedTrackId = settings.lastTrackId
            if (!savedTrackId.isNullOrBlank()) {
                val track = repository.getTrack(savedTrackId)
                if (track != null) {
                    _playbackState.update {
                        it.copy(
                            currentTrack = track,
                            tracks = listOf(track),
                            currentIndex = 0,
                            currentPositionMs = settings.lastPositionMs,
                            durationMs = track.durationSec * 1000L
                        )
                    }
                    val mediaItem = buildMediaItem(track)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.seekTo(settings.lastPositionMs)
                    exoPlayer.pause()
                }
            }
        }
    }

    fun playTrack(
        track: Track,
        contextTracks: List<Track>,
        contextType: PlaybackContextType = PlaybackContextType.QUEUE,
        contextId: String? = null
    ) {
        val tracks = if (contextTracks.isEmpty()) listOf(track) else contextTracks
        val index = tracks.indexOfFirst { it.videoId == track.videoId }.let { if (it >= 0) it else 0 }

        val shuffleOrder = if (_playbackState.value.isShuffle) {
            QueueHelper.generateShuffleOrder(tracks.size, index)
        } else {
            emptyList()
        }

        _playbackState.update {
            it.copy(
                currentTrack = track,
                tracks = tracks,
                currentIndex = index,
                contextType = contextType,
                contextId = contextId,
                shuffleOrder = shuffleOrder,
                shufflePosition = 0,
                isPlaying = true,
                isLoading = true,
                error = null
            )
        }

        val mediaItem = buildMediaItem(track)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        try {
            val serviceIntent = Intent(context, PlaybackService::class.java)
            context.startService(serviceIntent)
        } catch (_: Exception) {
        }

        scope.launch {
            repository.recordPlay(track)
            repository.saveQueue(tracks)
            repository.savePlaybackState(track.videoId, 0L)
            prefetchNextTracks()
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            _playbackState.update { it.copy(isPlaying = false, isLoading = false) }
            val track = _playbackState.value.currentTrack
            if (track != null) {
                scope.launch {
                    repository.savePlaybackState(track.videoId, exoPlayer.currentPosition)
                }
            }
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE && _playbackState.value.currentTrack != null) {
                val mediaItem = buildMediaItem(_playbackState.value.currentTrack!!)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            }
            exoPlayer.play()
            _playbackState.update { it.copy(isPlaying = true, isLoading = exoPlayer.playbackState == Player.STATE_BUFFERING) }
            try {
                val serviceIntent = Intent(context, PlaybackService::class.java)
                context.startService(serviceIntent)
            } catch (_: Exception) {
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun next() {
        val state = _playbackState.value
        val tracks = state.tracks
        if (tracks.isEmpty()) return

        when (state.repeatMode) {
            RepeatState.ONE -> {
                exoPlayer.seekTo(0)
                exoPlayer.play()
                return
            }
            else -> Unit
        }

        if (state.isShuffle && state.shuffleOrder.isNotEmpty()) {
            val nextShufflePos = state.shufflePosition + 1
            if (nextShufflePos < state.shuffleOrder.size) {
                val nextTrackIndex = state.shuffleOrder[nextShufflePos]
                val nextTrack = tracks[nextTrackIndex]
                _playbackState.update { it.copy(shufflePosition = nextShufflePos) }
                playTrack(nextTrack, tracks, state.contextType, state.contextId)
                return
            } else if (state.repeatMode == RepeatState.ALL) {
                val newShuffleOrder = QueueHelper.generateShuffleOrder(tracks.size, 0)
                val nextTrack = tracks[newShuffleOrder[0]]
                _playbackState.update { it.copy(shuffleOrder = newShuffleOrder, shufflePosition = 0) }
                playTrack(nextTrack, tracks, state.contextType, state.contextId)
                return
            }
        } else {
            val nextIndex = state.currentIndex + 1
            if (nextIndex < tracks.size) {
                playTrack(tracks[nextIndex], tracks, state.contextType, state.contextId)
                return
            } else if (state.repeatMode == RepeatState.ALL) {
                playTrack(tracks[0], tracks, state.contextType, state.contextId)
                return
            }
        }

        // End of list: check artist radio auto-play if context is ARTIST
        if (state.contextType == PlaybackContextType.ARTIST && state.currentTrack != null) {
            scope.launch {
                val moreSongs = repository.getArtistSongs(state.currentTrack.cleanArtist, state.currentTrack.videoId, limit = 20)
                if (moreSongs.isNotEmpty()) {
                    val combined = tracks + moreSongs
                    playTrack(moreSongs[0], combined, PlaybackContextType.ARTIST, state.contextId)
                    return@launch
                }
                exoPlayer.pause()
            }
        } else {
            exoPlayer.pause()
        }
    }

    fun previous() {
        if (exoPlayer.currentPosition > 3000L) {
            exoPlayer.seekTo(0)
            return
        }

        val state = _playbackState.value
        val tracks = state.tracks
        if (tracks.isEmpty()) return

        if (state.isShuffle && state.shuffleOrder.isNotEmpty()) {
            val prevShufflePos = state.shufflePosition - 1
            if (prevShufflePos >= 0) {
                val prevTrack = tracks[state.shuffleOrder[prevShufflePos]]
                _playbackState.update { it.copy(shufflePosition = prevShufflePos) }
                playTrack(prevTrack, tracks, state.contextType, state.contextId)
                return
            }
        }

        val prevIndex = state.currentIndex - 1
        if (prevIndex >= 0) {
            playTrack(tracks[prevIndex], tracks, state.contextType, state.contextId)
        } else if (state.repeatMode == RepeatState.ALL && tracks.isNotEmpty()) {
            playTrack(tracks[tracks.size - 1], tracks, state.contextType, state.contextId)
        } else {
            exoPlayer.seekTo(0)
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffle
        val tracks = _playbackState.value.tracks
        val currentIndex = _playbackState.value.currentIndex
        val shuffleOrder = if (newShuffle) {
            QueueHelper.generateShuffleOrder(tracks.size, currentIndex)
        } else {
            emptyList()
        }

        _playbackState.update {
            it.copy(isShuffle = newShuffle, shuffleOrder = shuffleOrder, shufflePosition = 0)
        }
        scope.launch {
            repository.setShuffle(newShuffle)
            prefetchNextTracks()
        }
    }

    fun toggleRepeat() {
        val nextRepeat = when (_playbackState.value.repeatMode) {
            RepeatState.OFF -> RepeatState.ALL
            RepeatState.ALL -> RepeatState.ONE
            RepeatState.ONE -> RepeatState.OFF
        }
        _playbackState.update { it.copy(repeatMode = nextRepeat) }
        scope.launch {
            repository.setRepeatMode(nextRepeat)
        }
    }

    fun updateColors(primary: Long, secondary: Long) {
        _playbackState.update { it.copy(primaryColor = primary, secondaryColor = secondary) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer.volume = clamped
        _playbackState.update { it.copy(volume = clamped) }
        scope.launch {
            repository.setVolume(clamped)
        }
    }

    private fun handleTrackEnded() {
        next()
    }

    private fun prefetchNextTracks() {
        val state = _playbackState.value
        val tracks = state.tracks
        if (tracks.isEmpty()) return

        val nextIndex = if (state.isShuffle && state.shuffleOrder.isNotEmpty()) {
            val pos = state.shufflePosition + 1
            if (pos < state.shuffleOrder.size) state.shuffleOrder[pos] else null
        } else {
            val idx = state.currentIndex + 1
            if (idx < tracks.size) idx else null
        }

        if (nextIndex != null && nextIndex in tracks.indices) {
            val nextTrack = tracks[nextIndex]
            scope.launch(Dispatchers.IO) {
                try {
                    extractor.resolveStreamUrl(nextTrack.videoId)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun buildMediaItem(track: Track): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.videoId)
            .setUri(Uri.parse("skullbeat://audio?id=${track.videoId}"))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(Uri.parse(track.thumbnailUrl))
                    .build()
            )
            .build()
    }
}
