package com.skullbeat.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRuleTest {

    private fun simulateRecordPlay(history: MutableList<String>, newVideoId: String) {
        history.remove(newVideoId)
        history.add(0, newVideoId)
        while (history.size > 30) {
            history.removeAt(history.size - 1)
        }
    }

    @Test
    fun historyDeduplicationAndOrder() {
        val history = mutableListOf("id1", "id2", "id3")
        simulateRecordPlay(history, "id2")

        assertEquals(listOf("id2", "id1", "id3"), history)
        assertEquals(3, history.size)
    }

    @Test
    fun historyLimitTo30() {
        val history = mutableListOf<String>()
        for (i in 1..40) {
            simulateRecordPlay(history, "id$i")
        }

        assertEquals(30, history.size)
        assertEquals("id40", history[0]) // Most recent
        assertEquals("id11", history[29]) // Oldest kept (1 to 10 dropped)
    }
}
