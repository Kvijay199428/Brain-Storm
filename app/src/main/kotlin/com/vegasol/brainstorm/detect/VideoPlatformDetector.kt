package com.vegasol.brainstorm.detect

import com.vegasol.brainstorm.ContentDetector
import com.vegasol.brainstorm.ContentObservation
import com.vegasol.brainstorm.DetectionOutcome
import com.vegasol.brainstorm.Support

/**
 * Clean result produced when a video detector observes an active content item or transition.
 */
data class VideoObservation(
    val packageName: String,
    val contentType: VideoContentType,
    val contentKey: String,
    val title: String? = null,
    val isPlaybackActive: Boolean = true,
    val confidence: Float = 0.6f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Universal interface for platform-specific video adapters.
 * Each platform provides its own markers, action filters, and fingerprinting rules,
 * while utilizing a shared event recording and reporting engine.
 */
interface VideoPlatformDetector : ContentDetector {
    val platformInfo: VideoPlatformInfo

    override val packages: Set<String> get() = setOf(platformInfo.packageName)
    override val support: Support get() = platformInfo.defaultSupport

    /** Analyzes an in-memory accessibility tree snapshot for video playback / content transitions. */
    fun analyze(snapshot: AccessibilitySnapshot): VideoObservation?

    override fun process(o: ContentObservation): DetectionOutcome {
        // Default bridge for backwards compatibility with legacy ContentDetector caller
        if (o.contentId.isNullOrBlank()) return DetectionOutcome(false, null, 0f)
        return DetectionOutcome(true, o.contentId, 0.6f)
    }
}
