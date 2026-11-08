package com.skullbeat.app.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skullbeat.app.domain.models.Playlist
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.repository.MusicRepository
import com.skullbeat.app.domain.util.FilterCriteria
import com.skullbeat.app.domain.util.TrackExporter
import com.skullbeat.app.domain.util.TrackFilter
import com.skullbeat.app.player.manager.PlaybackContextType
import com.skullbeat.app.player.manager.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab {
    ALL, LIKED, PLAYLISTS, HISTORY
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LibraryTab.ALL)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _filterCriteria = MutableStateFlow(FilterCriteria())
    val filterCriteria: StateFlow<FilterCriteria> = _filterCriteria.asStateFlow()

    val allTracks: StateFlow<List<Track>> = repository.getAllSavedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<Track>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyTracks: StateFlow<List<Track>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState = playerManager.playbackState

    // Live filtered tracks based on active tab and filter criteria
    val filteredTracks: StateFlow<List<Track>> = combine(
        _selectedTab,
        _filterCriteria,
        allTracks,
        favoriteTracks,
        historyTracks
    ) { tab, criteria, all, favs, hist ->
        val sourceList = when (tab) {
            LibraryTab.ALL -> all
            LibraryTab.LIKED -> favs
            LibraryTab.HISTORY -> hist
            LibraryTab.PLAYLISTS -> emptyList()
        }
        TrackFilter.filter(sourceList, criteria)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun updateFilterCriteria(criteria: FilterCriteria) {
        _filterCriteria.value = criteria
    }

    fun playTrack(track: Track, list: List<Track>) {
        val contextType = when (_selectedTab.value) {
            LibraryTab.LIKED -> PlaybackContextType.PLAYLIST
            else -> PlaybackContextType.QUEUE
        }
        playerManager.playTrack(track, list, contextType)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun exportFilteredM3u() {
        val list = filteredTracks.value
        val content = TrackExporter.toM3u(list)
        TrackExporter.shareExport(context, "m3u", content, "audio/x-mpegurl")
    }

    fun exportFilteredCsv() {
        val list = filteredTracks.value
        val content = TrackExporter.toCsv(list)
        TrackExporter.shareExport(context, "csv", content, "text/csv")
    }

    fun exportFilteredJson() {
        val list = filteredTracks.value
        val content = TrackExporter.toJson(list)
        TrackExporter.shareExport(context, "json", content, "application/json")
    }
}
