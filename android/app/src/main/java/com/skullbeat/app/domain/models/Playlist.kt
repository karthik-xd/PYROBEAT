package com.skullbeat.app.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val emoji: String = "🎵",
    val colorHex: String = "#3D8BFF",
    val coverPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
