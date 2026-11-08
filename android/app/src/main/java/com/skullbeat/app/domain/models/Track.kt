package com.skullbeat.app.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "Single",
    val durationSec: Long = 0L,
    val durationFormatted: String = "0:00",
    val thumbnailUrl: String = ""
) {
    val cleanArtist: String
        get() = artist.split(Regex("(?i)\\s*(?:,|&|\\bfeat\\.?|\\bft\\.?)\\s*")).firstOrNull()?.trim().orEmpty().ifEmpty { artist }
}
