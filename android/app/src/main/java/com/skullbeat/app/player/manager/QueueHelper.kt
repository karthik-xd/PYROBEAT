package com.skullbeat.app.player.manager

object QueueHelper {
    fun generateShuffleOrder(size: Int, currentIndex: Int): List<Int> {
        if (size <= 0) return emptyList()
        val indices = (0 until size).toMutableList()
        if (currentIndex in 0 until size) {
            indices.remove(currentIndex)
            indices.shuffle()
            indices.add(0, currentIndex)
        } else {
            indices.shuffle()
        }
        return indices
    }
}
