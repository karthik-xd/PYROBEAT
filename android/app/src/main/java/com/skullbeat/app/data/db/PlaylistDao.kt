package com.skullbeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.skullbeat.app.domain.models.Playlist
import com.skullbeat.app.domain.models.PlaylistTrack
import com.skullbeat.app.domain.models.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name, emoji = :emoji, colorHex = :colorHex WHERE id = :playlistId")
    suspend fun updatePlaylist(playlistId: Long, name: String, emoji: String, colorHex: String)

    @Query("UPDATE playlists SET name = :name, emoji = :emoji, colorHex = :colorHex, coverPath = :coverPath WHERE id = :playlistId")
    suspend fun updatePlaylistFull(playlistId: Long, name: String, emoji: String, colorHex: String, coverPath: String?)

    @Query("UPDATE playlists SET coverPath = :coverPath WHERE id = :playlistId")
    suspend fun updatePlaylistCover(playlistId: Long, coverPath: String?)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun updatePlaylistName(playlistId: Long, name: String)

    @Query("SELECT coverPath FROM playlists WHERE id = :playlistId")
    suspend fun getCoverPath(playlistId: Long): String?

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun getPlaylistFlow(playlistId: Long): Flow<Playlist?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrack)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(playlistTracks: List<PlaylistTrack>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, videoId: String)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun isTrackInPlaylist(playlistId: Long, videoId: String): Int

    @Transaction
    suspend fun addTrackToPlaylist(playlistId: Long, videoId: String) {
        val nextPos = (getMaxPosition(playlistId) ?: -1) + 1
        insertPlaylistTrack(PlaylistTrack(playlistId = playlistId, videoId = videoId, position = nextPos))
    }

    @Query("""
        SELECT t.* FROM tracks t 
        INNER JOIN playlist_tracks pt ON t.videoId = pt.videoId 
        WHERE pt.playlistId = :playlistId 
        ORDER BY pt.position ASC
    """)
    fun getPlaylistTracksFlow(playlistId: Long): Flow<List<Track>>

    @Query("""
        SELECT t.* FROM tracks t 
        INNER JOIN playlist_tracks pt ON t.videoId = pt.videoId 
        WHERE pt.playlistId = :playlistId 
        ORDER BY pt.position ASC
    """)
    suspend fun getPlaylistTracks(playlistId: Long): List<Track>
}
