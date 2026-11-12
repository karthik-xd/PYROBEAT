package com.skullbeat.app.domain.repository

import com.skullbeat.app.data.network.SpotifyImportResult
import com.skullbeat.app.data.storage.PlayerSettings
import com.skullbeat.app.data.storage.RepeatState
import com.skullbeat.app.domain.models.Playlist
import com.skullbeat.app.domain.models.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun search(query: String): List<Track>
    suspend fun getArtistSongs(artist: String, currentVideoId: String? = null, limit: Int = 20): List<Track>
    suspend fun resolveStreamUrl(videoId: String, forceRefresh: Boolean = false): String
    fun invalidateStreamCache(videoId: String)

    suspend fun importYouTubePlaylist(urlOrId: String): Pair<String, List<Track>>
    suspend fun importSpotify(urlOrUri: String, onProgress: (Int, Int) -> Unit): SpotifyImportResult
    suspend fun importCsvSongs(songs: List<Pair<String, String>>, onProgress: (matched: Int, total: Int, currentSong: String) -> Unit): Pair<String, List<Track>>

    suspend fun saveTrack(track: Track)
    suspend fun saveTracks(tracks: List<Track>)
    suspend fun getTrack(videoId: String): Track?
    fun getAllSavedTracks(): Flow<List<Track>>

    fun isFavorite(videoId: String): Flow<Boolean>
    suspend fun toggleFavorite(track: Track)
    fun getFavorites(): Flow<List<Track>>

    suspend fun createPlaylist(name: String, emoji: String = "🎵", colorHex: String = "#3D8BFF", coverPath: String? = null): Long
    suspend fun updatePlaylist(playlistId: Long, name: String, emoji: String = "🎵", colorHex: String = "#3D8BFF", coverPath: String? = null)
    suspend fun updatePlaylistCover(playlistId: Long, coverPath: String?)
    suspend fun updatePlaylistName(playlistId: Long, name: String)
    suspend fun isTrackInPlaylist(playlistId: Long, videoId: String): Boolean
    suspend fun deletePlaylist(playlistId: Long)
    fun getPlaylists(): Flow<List<Playlist>>
    fun getPlaylist(playlistId: Long): Flow<Playlist?>
    fun getPlaylistTracks(playlistId: Long): Flow<List<Track>>
    suspend fun addTrackToPlaylist(playlistId: Long, track: Track): Boolean
    suspend fun removeTrackFromPlaylist(playlistId: Long, videoId: String)

    suspend fun recordPlay(track: Track)
    fun getHistory(): Flow<List<Track>>
    suspend fun clearHistory()

    suspend fun saveQueue(tracks: List<Track>)
    fun getQueue(): Flow<List<Track>>

    val settingsFlow: Flow<PlayerSettings>
    suspend fun savePlaybackState(trackId: String?, positionMs: Long)
    suspend fun setShuffle(enabled: Boolean)
    suspend fun setRepeatMode(repeatState: RepeatState)
    suspend fun setVolume(volume: Float)
}
