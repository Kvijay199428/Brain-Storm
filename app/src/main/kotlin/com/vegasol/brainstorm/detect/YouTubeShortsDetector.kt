package com.vegasol.brainstorm.detect

/**
 * Experimental detector for YouTube's Shorts player.
 *
 * YouTube has no public API for "which short is on screen", so this reads the labels the player
 * exposes to Android's accessibility tree and looks for markers that only occur on the Shorts
 * surface. The service counts a new item when the visible signature changes (see [ShortsSignals]).
 *
 * It stays [com.vegasol.brainstorm.Support.EXPERIMENTAL] on purpose: YouTube can rename or remove
 * these labels in any release. Counts are therefore informational - the time limit remains the
 * enforced limit.
 */
class YouTubeShortsDetector : VerticalFeedDetector(
    packages = setOf(YOUTUBE),
    markers = MARKERS
) {
    companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val CONFIDENCE = VerticalFeedDetector.CONFIDENCE

        /**
         * Lower-case fragments of resource ids and content descriptions that occur in the Shorts
         * player. "remix this video" is the action label YouTube has shown for years; the
         * `shorts_*` / `reel_*` fragments cover the player containers. None of it is a public
         * contract, which is exactly why this detector is experimental.
         */
        val MARKERS = listOf(
            "remix this video",
            "shorts_player",
            "shorts_shelf",
            "reel_player",
            "reel_recycler",
            "reel_watch_fragment"
        )
    }
}
