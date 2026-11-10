package com.skullbeat.app.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skullbeat.app.data.storage.RepeatState
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.repository.MusicRepository
import com.skullbeat.app.player.manager.LoopMode
import com.skullbeat.app.player.manager.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val repository: MusicRepository
) : ViewModel() {

    val playbackState = playerManager.playbackState

    val isFavorite: StateFlow<Boolean> = repository.getFavorites()
        .map { favs ->
            val curId = playbackState.value.currentTrack?.videoId
            favs.any { it.videoId == curId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun next() = playerManager.next()
    fun previous() = playerManager.previous()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun toggleShuffle() = playerManager.toggleShuffle()
    fun toggleRepeat() = playerManager.toggleRepeat()
    fun setLoopMode(mode: LoopMode) = playerManager.setLoopMode(mode)
    fun removeFromUserQueue(index: Int) = playerManager.removeFromUserQueue(index)
    fun clearUserQueue() = playerManager.clearUserQueue()
    fun addToQueue(track: Track) = playerManager.addToUserQueue(track)
    fun setVolume(volume: Float) = playerManager.setVolume(volume)

    fun toggleFavorite() {
        val current = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            repository.toggleFavorite(current)
        }
    }

    fun playQueueTrack(track: Track) {
        playerManager.playTrack(
            track = track,
            contextTracks = playbackState.value.tracks,
            contextType = playbackState.value.contextType,
            contextId = playbackState.value.contextId
        )
    }
}
