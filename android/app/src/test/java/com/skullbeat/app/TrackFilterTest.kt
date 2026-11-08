package com.skullbeat.app

import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.domain.util.FilterCriteria
import com.skullbeat.app.domain.util.TrackFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackFilterTest {

    private val track1 = Track(
        videoId = "v1",
        title = "Resonance",
        artist = "HOME",
        album = "Odyssey",
        durationSec = 212L,
        durationFormatted = "3:32"
    )

    private val track2 = Track(
        videoId = "v2",
        title = "After Dark",
        artist = "Mr.Kitty",
        album = "Time",
        durationSec = 259L,
        durationFormatted = "4:19"
    )

    private val track3 = Track(
        videoId = "v3",
        title = "Metamorphosis",
        artist = "INTERWORLD",
        album = "Single",
        durationSec = 142L,
        durationFormatted = "2:22"
    )

    private val allTracks = listOf(track1, track2, track3)

    @Test
    fun filterByQuery_matchesTitleOrArtist() {
        val res = TrackFilter.filter(allTracks, FilterCriteria(query = "home"))
        assertEquals(1, res.size)
        assertEquals("v1", res[0].videoId)

        val res2 = TrackFilter.filter(allTracks, FilterCriteria(query = "dark"))
        assertEquals(1, res2.size)
        assertEquals("v2", res2[0].videoId)
    }

    @Test
    fun filterByArtist() {
        val res = TrackFilter.filter(allTracks, FilterCriteria(artist = "INTERWORLD"))
        assertEquals(1, res.size)
        assertEquals("v3", res[0].videoId)
    }

    @Test
    fun filterByDurationRange() {
        // Between 200s and 250s -> only track1 (212s)
        val res = TrackFilter.filter(allTracks, FilterCriteria(minSec = 200L, maxSec = 250L))
        assertEquals(1, res.size)
        assertEquals("v1", res[0].videoId)
    }

    @Test
    fun filterByCombinedCriteria() {
        val res = TrackFilter.filter(allTracks, FilterCriteria(artist = "Mr.Kitty", minSec = 200L))
        assertEquals(1, res.size)
        assertEquals("v2", res[0].videoId)

        val emptyRes = TrackFilter.filter(allTracks, FilterCriteria(artist = "Mr.Kitty", maxSec = 150L))
        assertEquals(0, emptyRes.size)
    }
}
