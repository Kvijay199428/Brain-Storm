package com.vegasol.brainstorm.detect

import com.vegasol.brainstorm.ContentObservation
import com.vegasol.brainstorm.DetectionOutcome
import com.vegasol.brainstorm.Support
import java.security.MessageDigest

/**
 * Experimental detector for YouTube's Shorts player.
 *
 * YouTube has no public API for "which short is on screen", so this reads the labels the player
 * exposes to Android's accessibility tree and looks for markers that only occur on the Shorts
 * surface. The service counts a new item when the visible signature changes.
 *
 * It stays [Support.EXPERIMENTAL] on purpose: YouTube can rename or remove these labels in any
 * release. Counts are therefore informational — the time limit remains the enforced limit.
 */
class YouTubeShortsDetector : VideoPlatformDetector {

    override val platformInfo: VideoPlatformInfo = VideoPlatformCatalog.find(YOUTUBE)
        ?: VideoPlatformInfo(
            packageName = YOUTUBE,
            platformName = "YouTube",
            category = VideoAppCategory.SHORT_VIDEO,
            supportedTypes = setOf(VideoContentType.SHORT),
            defaultSupport = Support.EXPERIMENTAL
        )

    override fun analyze(snapshot: AccessibilitySnapshot): VideoObservation? {
        val inShorts = snapshot.containsAnyMarker(MARKERS)
        if (!inShorts) return null

        val parts = snapshot.stableFingerprintParts(isAction = ::isAction)
        val raw = if (parts.isEmpty()) "yt:empty" else parts.joinToString("|")
        val hash = hash(raw)
        val key = "shorts:$hash:${snapshot.timestamp}"

        return VideoObservation(
            packageName = YOUTUBE,
            contentType = VideoContentType.SHORT,
            contentKey = key,
            confidence = CONFIDENCE,
            timestamp = snapshot.timestamp
        )
    }

    override fun process(o: ContentObservation): DetectionOutcome {
        val hit = o.markers.any { marker -> MARKERS.any { marker.contains(it) } }
        if (!hit || o.contentId.isNullOrBlank()) return DetectionOutcome(false, null, 0f)
        return DetectionOutcome(true, o.contentId, CONFIDENCE)
    }

    companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val CONFIDENCE = 0.6f

        val MARKERS = listOf(
            "remix this video",
            "shorts_player",
            "shorts_shelf",
            "reel_player",
            "reel_recycler",
            "reel_watch_fragment"
        )

        private val ACTION_LABELS = listOf(
            "like this video", "dislike this video", "share", "remix this video", "comments",
            "view comments", "subscribe", "unsubscribe", "more options", "search", "cast",
            "notifications", "create", "home", "shorts", "subscriptions", "library", "trending",
            "settings", "back", "menu"
        )

        fun isAction(text: String): Boolean = ACTION_LABELS.any { text.contains(it) }

        fun hash(text: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            return d.take(6).joinToString("") { "%02x".format(it) }
        }
    }
}
