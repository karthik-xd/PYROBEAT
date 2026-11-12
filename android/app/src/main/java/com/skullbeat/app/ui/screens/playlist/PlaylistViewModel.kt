package com.skullbeat.app.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skullbeat.app.domain.models.Playlist
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.repository.MusicRepository
import com.skullbeat.app.player.manager.PlaybackContextType
import com.skullbeat.app.player.manager.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    val playlistId: Long = savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: 0L

    val playlist: StateFlow<Playlist?> = repository.getPlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tracks: StateFlow<List<Track>> = repository.getPlaylistTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Track>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState = playerManager.playbackState

    fun playAll(shuffle: Boolean = false) {
        val list = tracks.value
        if (list.isNotEmpty()) {
            if (shuffle) {
                playerManager.toggleShuffle()
            }
            playerManager.playTrack(list[0], list, PlaybackContextType.PLAYLIST, playlistId.toString())
        }
    }

    fun addToQueue(track: Track) {
        playerManager.addToUserQueue(track)
    }

    fun removeFromQueue(track: Track) {
        playerManager.removeTrackFromUserQueue(track)
    }

    fun playTrack(track: Track) {
        playerManager.playTrack(track, tracks.value, PlaybackContextType.PLAYLIST, playlistId.toString())
    }

    fun removeTrack(videoId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, videoId)
        }
    }

    fun deletePlaylist(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            onComplete()
        }
    }

    fun updatePlaylist(name: String, emoji: String, colorHex: String, coverPath: String? = null) {
        viewModelScope.launch {
            repository.updatePlaylist(playlistId, name, emoji, colorHex, coverPath)
        }
    }

    fun updateCoverPhoto(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val oldCover = playlist.value?.coverPath
            val newPath = com.skullbeat.app.domain.util.ImageUtils.processAndSavePlaylistCover(
                context = context,
                sourceUri = uri,
                playlistId = playlistId,
                oldCoverPath = oldCover
            )
            if (newPath != null) {
                repository.updatePlaylistCover(playlistId, newPath)
            }
        }
    }

    fun removeCoverPhoto() {
        viewModelScope.launch {
            val oldCover = playlist.value?.coverPath
            com.skullbeat.app.domain.util.ImageUtils.deleteCoverFile(oldCover)
            repository.updatePlaylistCover(playlistId, null)
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }

    fun addTrack(track: Track) {
        viewModelScope.launch {
            try {
                repository.addTrackToPlaylist(playlistId, track)
            } catch (e: Exception) {
                android.util.Log.e("PlaylistViewModel", "Failed to add track", e)
            }
        }
    }

    suspend fun searchTracks(query: String): List<Track> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        return try {
            repository.search(clean)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
