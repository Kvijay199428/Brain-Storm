package com.vegasol.brainstorm.detect

import com.vegasol.brainstorm.Support

/** High-level category of an application for discovery and grouped setup. */
enum class VideoAppCategory(val label: String, val shortLabel: String) {
    SHORT_VIDEO("Short videos & reels", "Shorts/Reels"),
    SOCIAL("Social & community", "Social"),
    STREAMING("Movies & TV streaming", "Streaming"),
    MEDIA_PLAYER("Media players", "Video player")
}

/** Specific category of video content recognized by detectors. */
enum class VideoContentType(val label: String) {
    SHORT("Short / Reel"),
    VIDEO("Video"),
    MOVIE("Movie"),
    EPISODE("Episode")
}

/** Describes a known platform in the video detection catalogue. */
data class VideoPlatformInfo(
    val packageName: String,
    val platformName: String,
    val category: VideoAppCategory,
    val supportedTypes: Set<VideoContentType>,
    val defaultSupport: Support = Support.TIME_ONLY
)

/**
 * Maintained catalogue of video, short-form, social, and streaming platforms.
 * Differentiates universal usage time monitoring from platform-specific content detection.
 */
object VideoPlatformCatalog {

    val PLATFORMS: List<VideoPlatformInfo> = listOf(
        // Short-video platforms
        VideoPlatformInfo(
            packageName = "com.google.android.youtube",
            platformName = "YouTube",
            category = VideoAppCategory.SHORT_VIDEO,
            supportedTypes = setOf(VideoContentType.SHORT, VideoContentType.VIDEO),
            defaultSupport = Support.EXPERIMENTAL
        ),
        VideoPlatformInfo(
            packageName = "com.instagram.android",
            platformName = "Instagram",
            category = VideoAppCategory.SHORT_VIDEO,
            supportedTypes = setOf(VideoContentType.SHORT, VideoContentType.VIDEO),
            defaultSupport = Support.EXPERIMENTAL
        ),
        VideoPlatformInfo(
            packageName = "com.zhiliaoapp.musically",
            platformName = "TikTok",
            category = VideoAppCategory.SHORT_VIDEO,
            supportedTypes = setOf(VideoContentType.SHORT),
            defaultSupport = Support.TIME_ONLY
        ),
        VideoPlatformInfo(
            packageName = "com.snapchat.android",
            platformName = "Snapchat",
            category = VideoAppCategory.SHORT_VIDEO,
            supportedTypes = setOf(VideoContentType.SHORT),
            defaultSupport = Support.TIME_ONLY
        ),

        // Social platforms
        VideoPlatformInfo(
            packageName = "com.facebook.katana",
            platformName = "Facebook",
            category = VideoAppCategory.SOCIAL,
            supportedTypes = setOf(VideoContentType.SHORT, VideoContentType.VIDEO),
            defaultSupport = Support.TIME_ONLY
        ),
        VideoPlatformInfo(
            packageName = "com.reddit.frontpage",
            platformName = "Reddit",
            category = VideoAppCategory.SOCIAL,
            supportedTypes = setOf(VideoContentType.VIDEO),
            defaultSupport = Support.TIME_ONLY
        ),

        // Streaming & Movies
        VideoPlatformInfo(
            packageName = "com.netflix.mediaclient",
            platformName = "Netflix",
            category = VideoAppCategory.STREAMING,
            supportedTypes = setOf(VideoContentType.MOVIE, VideoContentType.EPISODE),
            defaultSupport = Support.TIME_ONLY
        ),
        VideoPlatformInfo(
            packageName = "com.amazon.avod.thirdpartyclient",
            platformName = "Amazon Prime Video",
            category = VideoAppCategory.STREAMING,
            supportedTypes = setOf(VideoContentType.MOVIE, VideoContentType.EPISODE),
            defaultSupport = Support.TIME_ONLY
        ),
        VideoPlatformInfo(
            packageName = "in.startv.hotstar",
            platformName = "Disney+ / Hotstar",
            category = VideoAppCategory.STREAMING,
            supportedTypes = setOf(VideoContentType.MOVIE, VideoContentType.EPISODE),
            defaultSupport = Support.TIME_ONLY
        ),

        // Media Players
        VideoPlatformInfo(
            packageName = "com.mxtech.videoplayer.ad",
            platformName = "MX Player",
            category = VideoAppCategory.MEDIA_PLAYER,
            supportedTypes = setOf(VideoContentType.VIDEO, VideoContentType.MOVIE),
            defaultSupport = Support.TIME_ONLY
        ),
        VideoPlatformInfo(
            packageName = "org.videolan.vlc",
            platformName = "VLC",
            category = VideoAppCategory.MEDIA_PLAYER,
            supportedTypes = setOf(VideoContentType.VIDEO),
            defaultSupport = Support.TIME_ONLY
        )
    )

    private val BY_PACKAGE = PLATFORMS.associateBy { it.packageName }

    fun find(packageName: String): VideoPlatformInfo? = BY_PACKAGE[packageName]

    fun category(packageName: String): VideoAppCategory? = find(packageName)?.category

    fun allPackages(): Set<String> = BY_PACKAGE.keys
}
