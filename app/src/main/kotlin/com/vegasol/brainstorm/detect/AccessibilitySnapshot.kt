package com.vegasol.brainstorm.detect

/**
 * In-memory representation of an active accessibility window scan.
 * Raw accessibility labels are evaluated in-memory and NEVER persisted to disk or transmitted.
 */
data class AccessibilitySnapshot(
    val packageName: String,
    val timestamp: Long,
    val viewIds: List<String>,
    val textLabels: List<String>,
    val contentDescriptions: List<String>
) {
    val allMarkers: Set<String> by lazy {
        (viewIds.map { it.lowercase().trim() } +
         textLabels.map { it.lowercase().trim() } +
         contentDescriptions.map { it.lowercase().trim() })
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** Checks whether any normalized token contains any marker substring. */
    fun containsAnyMarker(markers: Collection<String>): Boolean {
        return markers.any { m -> allMarkers.any { it.contains(m) } }
    }

    /**
     * Extracts non-volatile text fragments to construct a stable item fingerprint.
     * Excludes like counts, subscriber counts, timestamps, and universal action buttons.
     */
    fun stableFingerprintParts(
        isAction: (String) -> Boolean,
        maxParts: Int = 24
    ): List<String> {
        return (textLabels + contentDescriptions)
            .map { it.trim().lowercase() }
            .filter { it.length in 2..120 }
            .filterNot(isAction)
            .filterNot { VOLATILE.matches(it) }
            .filterNot { COUNTER.matches(it) }
            .distinct()
            .sorted()
            .take(maxParts)
    }

    companion object {
        private val VOLATILE = Regex("^[\\d\\s.,:%+\\-–—/kKmMbB]+$")
        private val COUNTER = Regex("^[\\d.,]+\\s*[kmb]?\\s*(likes?|views?|comments?|subscribers?|shares?)$")
    }
}
