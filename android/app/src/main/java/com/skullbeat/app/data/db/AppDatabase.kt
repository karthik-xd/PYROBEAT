package com.skullbeat.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN coverPath TEXT DEFAULT NULL")
            }
        }
    }
}
