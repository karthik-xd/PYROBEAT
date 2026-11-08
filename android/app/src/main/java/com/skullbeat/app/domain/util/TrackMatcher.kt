package com.skullbeat.app.domain.util

import com.skullbeat.app.domain.models.Track

object TrackMatcher {

    private val UNWANTED_TAGS = listOf(
        "reaction", "review", "tutorial", "lesson", "how to", "parody",
        "10 hours", "10 hour", "1 hour", "8 hours", "loop", "earrape"
    )

    private val CLEANUP_REGEX = Regex("""(?i)\s*[\(\[\{]?(?:official\s*(?:video|audio|music\s*video|lyric\s*video|visualizer)|lyrics?|hq|hd|4k|audio)[\)\]\}]?""")
    private val FEAT_REGEX = Regex("""(?i)\s*(?:feat\.?|ft\.?)\s+.*""")
    private val PUNCT_REGEX = Regex("""[^\p{L}\p{Nd}\s]""")

    fun normalize(text: String): String {
        return text
            .replace(CLEANUP_REGEX, " ")
            .replace(FEAT_REGEX, " ")
            .replace(PUNCT_REGEX, " ")
            .lowercase()
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    fun extractTokens(text: String): Set<String> {
        return normalize(text)
            .split(" ")
            .filter { it.length >= 2 }
            .toSet()
    }

    /**
     * Calculates a similarity score between 0.0 and 1.0 between requested (title, artist)
     * and a candidate YouTube track.
     */
    fun calculateSimilarity(
        targetTitle: String,
        targetArtist: String,
        candidate: Track
    ): Double {
        val targetTitleTokens = extractTokens(targetTitle)
        if (targetTitleTokens.isEmpty()) return 0.0

        val candidateTitleTokens = extractTokens(candidate.title)
        val candidateArtistTokens = extractTokens(candidate.artist)
        val combinedCandidateTokens = candidateTitleTokens + candidateArtistTokens

        // 1. Title token overlap (Jaccard-like recall on target words)
        val matchedTitleTokens = targetTitleTokens.intersect(combinedCandidateTokens).size
        val titleScore = matchedTitleTokens.toDouble() / targetTitleTokens.size.toDouble()

        // 2. Artist match bonus
        val targetArtistTokens = extractTokens(targetArtist)
        val artistMatched = if (targetArtistTokens.isNotEmpty()) {
            val matchedArtist = targetArtistTokens.intersect(combinedCandidateTokens).size
            matchedArtist.toDouble() / targetArtistTokens.size.toDouble()
        } else {
            0.5 // neutral if no artist specified
        }

        // 3. Penalty for unwanted keywords
        val candidateLower = candidate.title.lowercase()
        var penalty = 0.0
        for (unwanted in UNWANTED_TAGS) {
            if (candidateLower.contains(unwanted) && !targetTitle.lowercase().contains(unwanted)) {
                penalty += 0.4
            }
        }

        val totalScore = (titleScore * 0.7) + (artistMatched * 0.3) - penalty
        return totalScore.coerceIn(0.0, 1.0)
    }

    /**
     * Finds the best matching track from candidates with minimum acceptable threshold.
     */
    fun findBestMatch(
        targetTitle: String,
        targetArtist: String,
        candidates: List<Track>,
        minScoreThreshold: Double = 0.40
    ): Track? {
        if (candidates.isEmpty()) return null

        val scored = candidates.map { candidate ->
            candidate to calculateSimilarity(targetTitle, targetArtist, candidate)
        }

        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second >= minScoreThreshold) best.first else null
    }
}
