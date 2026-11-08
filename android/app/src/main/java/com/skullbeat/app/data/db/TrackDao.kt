package com.skullbeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.skullbeat.app.domain.models.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<Track>)

    @Query("SELECT * FROM tracks WHERE videoId = :videoId LIMIT 1")
    suspend fun getTrack(videoId: String): Track?

    @Query("SELECT * FROM tracks WHERE videoId IN (:videoIds)")
    suspend fun getTracks(videoIds: List<String>): List<Track>

    @Query("SELECT * FROM tracks")
    fun getAllTracksFlow(): Flow<List<Track>>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracks(): List<Track>
}
