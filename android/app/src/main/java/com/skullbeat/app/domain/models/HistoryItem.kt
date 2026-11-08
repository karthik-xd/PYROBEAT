package com.skullbeat.app.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey val videoId: String,
    val playedAt: Long = System.currentTimeMillis()
)
