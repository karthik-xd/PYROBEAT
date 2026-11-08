package com.skullbeat.app

import com.skullbeat.app.player.manager.QueueHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueHelperTest {

    @Test
    fun generateShuffleOrder_currentTrackIsFirst() {
        val size = 10
        val currentIndex = 4
        val order = QueueHelper.generateShuffleOrder(size, currentIndex)

        assertEquals(size, order.size)
        assertEquals(currentIndex, order[0])
        assertEquals((0 until size).toSet(), order.toSet())
    }

    @Test
    fun generateShuffleOrder_emptyOrSingle() {
        assertTrue(QueueHelper.generateShuffleOrder(0, 0).isEmpty())

        val single = QueueHelper.generateShuffleOrder(1, 0)
        assertEquals(listOf(0), single)
    }
}
