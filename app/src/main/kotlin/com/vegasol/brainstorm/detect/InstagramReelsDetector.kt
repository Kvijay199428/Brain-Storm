package com.vegasol.brainstorm.detect

import com.vegasol.brainstorm.ContentObservation
import com.vegasol.brainstorm.DetectionOutcome
import com.vegasol.brainstorm.Support
import java.security.MessageDigest

/**
 * Experimental detector for Instagram Reels.
 *
 * Reads accessibility labels and view IDs from Instagram's Reels surface to detect new clips.
 * Ignores like counts, comments count, and action buttons so active engagement does not trigger
 * duplicate counts.
 */
class InstagramReelsDetector : VideoPlatformDetector {

    override val platformInfo: VideoPlatformInfo = VideoPlatformCatalog.find(INSTAGRAM)
        ?: VideoPlatformInfo(
            packageName = INSTAGRAM,
            platformName = "Instagram",
            category = VideoAppCategory.SHORT_VIDEO,
            supportedTypes = setOf(VideoContentType.SHORT, VideoContentType.VIDEO),
            defaultSupport = Support.EXPERIMENTAL
        )

    override fun analyze(snapshot: AccessibilitySnapshot): VideoObservation? {
        val inReels = snapshot.containsAnyMarker(REELS_MARKERS)
        if (!inReels) return null

        val parts = snapshot.stableFingerprintParts(isAction = ::isAction)
        val raw = if (parts.isEmpty()) "ig:empty" else parts.joinToString("|")
        val hash = hash(raw)
        val key = "reels:$hash:${snapshot.timestamp}"

        return VideoObservation(
            packageName = INSTAGRAM,
            contentType = VideoContentType.SHORT,
            contentKey = key,
            confidence = CONFIDENCE,
            timestamp = snapshot.timestamp
        )
    }

    override fun process(o: ContentObservation): DetectionOutcome {
        val hit = o.markers.any { marker -> REELS_MARKERS.any { marker.contains(it) } }
        if (!hit || o.contentId.isNullOrBlank()) return DetectionOutcome(false, null, 0f)
        return DetectionOutcome(true, o.contentId, CONFIDENCE)
    }

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val CONFIDENCE = 0.6f

        val REELS_MARKERS = listOf(
            "clips_viewer",
            "clips_video",
            "reel_viewer",
            "clips_action_bar",
            "clips_audio_title",
            "clips_author_name",
            "reel_swipe_indicator",
            "reels_tray",
            "reel_recycler",
            "reel_tag"
        )

        private val ACTION_LABELS = listOf(
            "like", "liked", "double tap to like", "comment", "comments",
            "share", "share to", "remix", "remix this reel", "save", "saved",
            "audio", "original audio", "follow", "following", "more actions",
            "profile picture", "send message", "direct", "bookmark"
        )

        fun isAction(text: String): Boolean = ACTION_LABELS.any { text == it || text.contains(it) }

        fun hash(text: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            return d.take(6).joinToString("") { "%02x".format(it) }
        }
    }
}
