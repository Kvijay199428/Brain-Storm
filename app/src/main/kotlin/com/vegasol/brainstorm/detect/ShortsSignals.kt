package com.vegasol.brainstorm.detect

import java.security.MessageDigest

/** What one accessibility snapshot of the foreground window contains. */
data class UiSignals(
    val inShorts: Boolean,
    val fingerprint: String?,
    val markers: Set<String>
)

/**
 * Pure analysis of an accessibility snapshot. It deliberately takes plain strings instead of
 * [android.view.accessibility.AccessibilityNodeInfo] so the whole decision is unit-tested off
 * device; [ContentAccessibilityService] only walks the tree and feeds the lists in.
 */
object ShortsSignals {
    private val ACTION_LABELS = listOf(
        "like this video", "dislike this video", "share", "remix this video", "comments",
        "view comments", "subscribe", "unsubscribe", "more options", "search", "cast",
        "notifications", "create", "home", "shorts", "subscriptions", "library", "trending",
        "settings", "back", "menu"
    )
    private val VOLATILE = Regex("^[\\d\\s.,:%+\\-–—/kKmMbB]+$")
    private val COUNTER = Regex("^[\\d.,]+\\s*[kmb]?\\s*(likes?|views?|comments?|subscribers?|shares?)$")
    private const val MAX_PART = 48
    private const val MAX_PARTS = 24

    fun analyse(
        ids: Collection<String>,
        texts: Collection<String>,
        descriptions: Collection<String>,
        markers: Collection<String>
    ): UiSignals {
        val normIds = ids.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val normTexts = texts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val normDesc = descriptions.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        val seen = (normIds + normDesc + normTexts).toSet()
        val inShorts = seen.any { m -> markers.any { m.contains(it) } }

        // Engagement counts, durations and the action row itself must not change the fingerprint,
        // otherwise "1234 likes" would be counted as a new video.
        val parts = (normTexts + normDesc)
            .filter { it.length in 2..120 }
            .filterNot { isAction(it) }
            .filterNot { VOLATILE.matches(it) }
            .filterNot { COUNTER.matches(it) }
            .distinct()
            .sorted()
            .take(MAX_PARTS)
            .map { it.take(MAX_PART) }

        val fingerprint = if (parts.isEmpty()) null else hash(parts.joinToString("|"))
        return UiSignals(inShorts, fingerprint, seen)
    }

    private fun isAction(value: String) =
        ACTION_LABELS.any { value == it || value.startsWith("$it ") }

    fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
