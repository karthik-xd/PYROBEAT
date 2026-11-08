package com.skullbeat.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.skullbeat.app.domain.models.Favorite
import com.skullbeat.app.domain.models.HistoryItem
import com.skullbeat.app.domain.models.Playlist
import com.skullbeat.app.domain.models.PlaylistTrack
import com.skullbeat.app.domain.models.QueueItem
import com.skullbeat.app.domain.models.Track

@Database(
    entities = [
        Track::class,
        Favorite::class,
        Playlist::class,
        PlaylistTrack::class,
        HistoryItem::class,
        QueueItem::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
}
