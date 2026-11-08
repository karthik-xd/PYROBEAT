package com.skullbeat.app.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue")
data class QueueItem(
    @PrimaryKey val position: Int,
    val videoId: String
)
