package com.skullbeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.skullbeat.app.domain.models.QueueItem
import com.skullbeat.app.domain.models.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("DELETE FROM queue")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItem>)

    @Transaction
    suspend fun setQueue(videoIds: List<String>) {
        clearQueue()
        val items = videoIds.mapIndexed { index, videoId -> QueueItem(position = index, videoId = videoId) }
        insertQueueItems(items)
    }

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN queue q ON t.videoId = q.videoId
        ORDER BY q.position ASC
    """)
    fun getQueueTracksFlow(): Flow<List<Track>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN queue q ON t.videoId = q.videoId
        ORDER BY q.position ASC
    """)
    suspend fun getQueueTracks(): List<Track>
}
