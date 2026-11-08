package com.skullbeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.skullbeat.app.domain.models.HistoryItem
import com.skullbeat.app.domain.models.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: HistoryItem)

    @Query("DELETE FROM history WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM history WHERE videoId NOT IN (SELECT videoId FROM history ORDER BY playedAt DESC LIMIT 30)")
    suspend fun trimHistoryTo30()

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Transaction
    suspend fun recordPlay(videoId: String) {
        deleteByVideoId(videoId)
        insertHistoryItem(HistoryItem(videoId = videoId, playedAt = System.currentTimeMillis()))
        trimHistoryTo30()
    }

    @Query("SELECT t.* FROM tracks t INNER JOIN history h ON t.videoId = h.videoId ORDER BY h.playedAt DESC")
    fun getHistoryTracksFlow(): Flow<List<Track>>

    @Query("SELECT t.* FROM tracks t INNER JOIN history h ON t.videoId = h.videoId ORDER BY h.playedAt DESC LIMIT :limit")
    suspend fun getRecentTracks(limit: Int = 30): List<Track>
}
