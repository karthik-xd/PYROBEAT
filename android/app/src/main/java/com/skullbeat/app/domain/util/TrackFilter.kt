package com.skullbeat.app.domain.util

import com.skullbeat.app.domain.models.Track

data class FilterCriteria(
    val query: String = "",
    val artist: String = "",
    val title: String = "",
    val album: String = "",
    val minSec: Long = 0L,
    val maxSec: Long = Long.MAX_VALUE
)

object TrackFilter {
    fun filter(tracks: List<Track>, criteria: FilterCriteria): List<Track> {
        return tracks.filter predicate@{ track ->
            if (criteria.query.isNotBlank()) {
                val q = criteria.query.trim().lowercase()
                val match = track.title.lowercase().contains(q) ||
                        track.artist.lowercase().contains(q) ||
                        track.album.lowercase().contains(q)
                if (!match) return@predicate false
            }

            if (criteria.artist.isNotBlank() && !track.artist.contains(criteria.artist.trim(), ignoreCase = true)) {
                return@predicate false
            }

            if (criteria.title.isNotBlank() && !track.title.contains(criteria.title.trim(), ignoreCase = true)) {
                return@predicate false
            }

            if (criteria.album.isNotBlank() && !track.album.contains(criteria.album.trim(), ignoreCase = true)) {
                return@predicate false
            }

            if (track.durationSec < criteria.minSec || track.durationSec > criteria.maxSec) {
                return@predicate false
            }

            true
        }
    }
}
