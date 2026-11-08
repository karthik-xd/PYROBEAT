package com.skullbeat.app

import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.util.TrackMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    private fun makeCandidate(id: String, title: String, artist: String): Track {
        return Track(
            videoId = id,
            title = title,
            artist = artist,
            album = "Single",
            durationSec = 200,
            durationFormatted = "3:20",
            thumbnailUrl = ""
        )
    }

    @Test
    fun matchesIdenticalTitleAndArtist() {
        val candidates = listOf(
            makeCandidate("1", "Starboy (Official Audio)", "The Weeknd"),
            makeCandidate("2", "Starboy - Guitar Tutorial", "Music Lessons"),
            makeCandidate("3", "Unrelated Song", "Random Artist")
        )

        val best = TrackMatcher.findBestMatch("Starboy", "The Weeknd", candidates)
        assertNotNull(best)
        assertEquals("1", best?.videoId)
    }

    @Test
    fun penalizesReactionAndTutorialVideos() {
        val candidates = listOf(
            makeCandidate("1", "Midnight City - Vocal Coach Reaction", "Reaction King"),
            makeCandidate("2", "M83 - Midnight City (Official Video)", "M83VEVO")
        )

        val best = TrackMatcher.findBestMatch("Midnight City", "M83", candidates)
        assertNotNull(best)
        assertEquals("2", best?.videoId)
    }

    @Test
    fun rejectsCompletelyUnrelatedCandidates() {
        val candidates = listOf(
            makeCandidate("1", "Cooking Pasta 101", "Chef Master"),
            makeCandidate("2", "Top 10 Cars of 2024", "Auto Daily")
        )

        val best = TrackMatcher.findBestMatch("Resonance", "HOME", candidates, minScoreThreshold = 0.4)
        assertNull(best)
    }
}
