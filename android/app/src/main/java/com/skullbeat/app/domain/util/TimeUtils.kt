package com.skullbeat.app.domain.util

object TimeUtils {
    fun parseDuration(duration: String?): Long {
        if (duration.isNullOrBlank()) return 0L
        val trimmed = duration.trim()
        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                1 -> parts[0].toLongOrNull() ?: 0L
                2 -> {
                    val m = parts[0].toLongOrNull() ?: 0L
                    val s = parts[1].toLongOrNull() ?: 0L
                    m * 60 + s
                }
                3 -> {
                    val h = parts[0].toLongOrNull() ?: 0L
                    val m = parts[1].toLongOrNull() ?: 0L
                    val s = parts[2].toLongOrNull() ?: 0L
                    h * 3600 + m * 60 + s
                }
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "0:00"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
