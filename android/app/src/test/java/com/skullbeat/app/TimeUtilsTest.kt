package com.skullbeat.app

import com.skullbeat.app.domain.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun parseDuration_secondsOnly() {
        assertEquals(45L, TimeUtils.parseDuration("45"))
    }

    @Test
    fun parseDuration_minutesAndSeconds() {
        assertEquals(215L, TimeUtils.parseDuration("3:35"))
        assertEquals(65L, TimeUtils.parseDuration("1:05"))
    }

    @Test
    fun parseDuration_hoursMinutesSeconds() {
        assertEquals(3665L, TimeUtils.parseDuration("1:01:05"))
    }

    @Test
    fun parseDuration_invalidOrBlank() {
        assertEquals(0L, TimeUtils.parseDuration(null))
        assertEquals(0L, TimeUtils.parseDuration(""))
        assertEquals(0L, TimeUtils.parseDuration("abc"))
    }

    @Test
    fun formatDuration_test() {
        assertEquals("0:00", TimeUtils.formatDuration(0L))
        assertEquals("3:35", TimeUtils.formatDuration(215L))
        assertEquals("1:01:05", TimeUtils.formatDuration(3665L))
    }
}
