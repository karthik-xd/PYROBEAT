package com.skullbeat.app.player.manager

import com.skullbeat.app.data.storage.RepeatState
import com.skullbeat.app.domain.models.Track

enum class PlaybackContextType {
    PLAYLIST,
    ARTIST,
    QUEUE
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val contextType: PlaybackContextType = PlaybackContextType.QUEUE,
    val contextId: String? = null,
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleOrder: List<Int> = emptyList(),
    val shufflePosition: Int = 0,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatState = RepeatState.OFF,
    val volume: Float = 1.0f,
    val error: String? = null,
    val primaryColor: Long? = null,
    val secondaryColor: Long? = null
)
