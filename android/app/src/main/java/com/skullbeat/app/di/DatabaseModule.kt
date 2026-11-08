package com.skullbeat.app.di

import android.content.Context
import androidx.room.Room
import com.skullbeat.app.data.db.AppDatabase
import com.skullbeat.app.data.db.FavoriteDao
import com.skullbeat.app.data.db.HistoryDao
import com.skullbeat.app.data.db.PlaylistDao
import com.skullbeat.app.data.db.QueueDao
import com.skullbeat.app.data.db.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "skullbeat.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideQueueDao(db: AppDatabase): QueueDao = db.queueDao()
}
