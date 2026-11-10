package com.skullbeat.app.ui.screens.home

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
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    val historyTracks: StateFlow<List<Track>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Track>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState = playerManager.playbackState

    private val _featuredTracks = MutableStateFlow<List<Track>>(emptyList())
    val featuredTracks: StateFlow<List<Track>> = _featuredTracks.asStateFlow()

    private val _madeForYouTracks = MutableStateFlow<List<Track>>(emptyList())
    val madeForYouTracks: StateFlow<List<Track>> = _madeForYouTracks.asStateFlow()

    private val _isLoadingFeatured = MutableStateFlow(false)
    val isLoadingFeatured: StateFlow<Boolean> = _isLoadingFeatured.asStateFlow()

    init {
        loadFeatured()
        loadMadeForYou()
    }

    fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun loadFeatured(genre: String = "Synthwave") {
        viewModelScope.launch {
            _isLoadingFeatured.value = true
            try {
                val results = repository.search("$genre music")
                _featuredTracks.value = results.take(16)
            } catch (_: Throwable) {
            } finally {
                _isLoadingFeatured.value = false
            }
        }
    }

    fun loadMadeForYou() {
        viewModelScope.launch {
            try {
                val results = repository.search("Cyberpunk dark synth")
                _madeForYouTracks.value = results.take(12)
            } catch (_: Throwable) {
            }
        }
    }

    fun playTrack(track: Track, list: List<Track>) {
        playerManager.playTrack(track, list, PlaybackContextType.QUEUE)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }
}
