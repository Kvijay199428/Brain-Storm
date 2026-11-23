package com.vegasol.brainstorm

/** Pure time-interval helpers shared by usage accounting. No Android or database types. */
object UsageVisibilityTracker {

    data class TimeSpan(val startMs: Long, val endMs: Long)

    /**
     * Merge overlapping or touching spans. Two activities of the same package (for example a main
     * activity and a PiP window) must not book the same wall-clock second twice.
     */
    fun merge(spans: List<TimeSpan>): List<TimeSpan> {
        val sorted = spans.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        val out = ArrayList<TimeSpan>()
        for (span in sorted) {
            val last = out.lastOrNull()
            if (last == null || span.startMs > last.endMs) {
                out += span
            } else if (span.endMs > last.endMs) {
                out[out.lastIndex] = TimeSpan(last.startMs, span.endMs)
            }
        }
        return out
    }

    fun totalMillis(spans: List<TimeSpan>): Long = merge(spans).sumOf { it.endMs - it.startMs }

    /**
     * Event-derived foreground time and UsageStats visible time overlap; never add them. The larger
     * value is the honest lower bound of time the package was on screen (including PiP).
     */
    fun reconcileSeconds(foregroundSeconds: Long, visibleSeconds: Long): Long =
        maxOf(foregroundSeconds.coerceAtLeast(0L), visibleSeconds.coerceAtLeast(0L))
}
