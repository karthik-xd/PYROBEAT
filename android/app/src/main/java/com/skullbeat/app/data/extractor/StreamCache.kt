package com.skullbeat.app.data.extractor

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class CachedStream(
    val url: String,
    val expiresAtMs: Long
)

@Singleton
class StreamCache @Inject constructor() {
    private val cache = ConcurrentHashMap<String, CachedStream>()

    fun get(videoId: String): String? {
        val entry = cache[videoId] ?: return null
        val now = System.currentTimeMillis()
        return if (now < entry.expiresAtMs - 60_000L) {
            entry.url
        } else {
            cache.remove(videoId)
            null
        }
    }

    fun put(videoId: String, url: String) {
        val expiresAt = parseExpiry(url) ?: (System.currentTimeMillis() + 5 * 3600 * 1000L)
        cache[videoId] = CachedStream(url, expiresAt)
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    private fun parseExpiry(url: String): Long? {
        return try {
            val expireParam = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)
            expireParam?.toLongOrNull()?.let { it * 1000L }
        } catch (_: Exception) {
            null
        }
    }
}
