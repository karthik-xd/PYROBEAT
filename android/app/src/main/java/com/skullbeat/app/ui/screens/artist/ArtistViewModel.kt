package com.skullbeat.app.ui.screens.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    val artistName: String = try {
        val raw = savedStateHandle.get<String>("artistName").orEmpty()
        URLDecoder.decode(raw, "UTF-8")
    } catch (_: Exception) {
        ""
    }

    private val _songs = MutableStateFlow<List<Track>>(emptyList())
    val songs: StateFlow<List<Track>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val favorites: StateFlow<List<Track>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState = playerManager.playbackState

    init {
        loadArtistSongs()
    }

    private fun loadArtistSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = repository.getArtistSongs(artistName, limit = 30)
                _songs.value = results
            } catch (_: Exception) {
                _songs.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playAll() {
        val list = _songs.value
        if (list.isNotEmpty()) {
            playerManager.playTrack(list[0], list, PlaybackContextType.ARTIST, artistName)
        }
    }

    fun playTrack(track: Track) {
        playerManager.playTrack(track, _songs.value, PlaybackContextType.ARTIST, artistName)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }

    fun addToQueue(track: Track) {
        playerManager.addToUserQueue(track)
    }

    fun removeFromQueue(track: Track) {
        playerManager.removeTrackFromUserQueue(track)
    }
}
