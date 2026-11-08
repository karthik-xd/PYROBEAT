package com.skullbeat.app.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val videoId: String,
    val addedAt: Long = System.currentTimeMillis()
)
