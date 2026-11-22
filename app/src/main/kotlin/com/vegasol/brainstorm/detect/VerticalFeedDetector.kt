package com.vegasol.brainstorm.detect

import com.vegasol.brainstorm.ContentDetector
import com.vegasol.brainstorm.ContentObservation
import com.vegasol.brainstorm.DetectionOutcome
import com.vegasol.brainstorm.Support

/**
 * Shared counting rule for vertical short-form feeds.
 *
 * Every app names its player differently, so each detector supplies its own [markers]; the rule
 * itself is the same: the window has to show a surface marker and the accessibility snapshot has to
 * produce a content key. A detector that cannot see a marker never counts, which keeps an app's
 * normal home feed out of the total.
 */
abstract class VerticalFeedDetector(
    override val packages: Set<String>,
    override val markers: List<String>,
    override val support: Support = Support.EXPERIMENTAL
) : ContentDetector {

    override fun process(o: ContentObservation): DetectionOutcome {
        val hit = o.markers.any { marker -> markers.any { marker.contains(it) } }
        if (!hit || o.contentId.isNullOrBlank()) return DetectionOutcome(false, null, 0f)
        return DetectionOutcome(true, o.contentId, CONFIDENCE)
    }

    companion object {
        const val CONFIDENCE = 0.6f
    }
}
