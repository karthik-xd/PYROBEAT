package com.skullbeat.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.repository.MusicRepository
import com.skullbeat.app.player.manager.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _importLinkDetected = MutableStateFlow<String?>(null)
    val importLinkDetected: StateFlow<String?> = _importLinkDetected.asStateFlow()

    val favorites: StateFlow<List<Track>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState = playerManager.playbackState

    private var searchJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        val clean = newQuery.trim()
        if (clean.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            _importLinkDetected.value = null
            return
        }

        if (isImportLink(clean)) {
            _importLinkDetected.value = clean
            return
        } else {
            _importLinkDetected.value = null
        }

        searchJob = viewModelScope.launch {
            delay(350)
            executeSearch(clean)
        }
    }

    fun onSearchActionPressed() {
        val clean = _query.value.trim()
        if (clean.isNotBlank()) {
            searchJob?.cancel()
            if (isImportLink(clean)) {
                _importLinkDetected.value = clean
            } else {
                viewModelScope.launch {
                    executeSearch(clean)
                }
            }
        }
    }

    private suspend fun executeSearch(cleanQuery: String) {
        _isSearching.value = true
        try {
            val results = repository.search(cleanQuery)
            _searchResults.value = results
        } catch (_: Throwable) {
            _searchResults.value = emptyList()
        } finally {
            _isSearching.value = false
        }
    }

    private fun isImportLink(url: String): Boolean {
        return url.contains("spotify.com") ||
                url.startsWith("spotify:") ||
                url.contains("youtube.com/playlist") ||
                url.contains("music.youtube.com/playlist") ||
                url.contains("list=")
    }

    fun dismissImportPrompt() {
        _importLinkDetected.value = null
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }
}
