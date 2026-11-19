package com.vegasol.brainstorm.detect

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.vegasol.brainstorm.ContentObservation
import com.vegasol.brainstorm.ContentRepository
import com.vegasol.brainstorm.DetectionOutcome
import com.vegasol.brainstorm.Detectors
import com.vegasol.brainstorm.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Opt-in reader for surfaces that have a detector. The service filters events dynamically
 * to packages with registered and enabled detectors. Nothing is persisted except a content key
 * per counted item: the labels used to recognise the surface and fingerprint the item are
 * discarded in-memory immediately after the decision.
 *
 * The service only becomes active when the user turns it on in Android's accessibility settings.
 * Without it, the app reports content counting as unavailable instead of guessing.
 */
class ContentAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastFingerprint: String? = null
    private var lastCountAt = 0L
    private var lastScanAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Graph.init(applicationContext)
        updatePackageFilter()
        Log.i(TAG, "content detection service connected with multi-platform support")
    }

    /**
     * Dynamically configures the accessibility package filter so Android only delivers
     * events from packages with registered detectors.
     */
    fun updatePackageFilter(specificPackages: Set<String>? = null) {
        try {
            val targets = specificPackages ?: Detectors.all.flatMap { it.packages }.toSet()
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.packageNames = targets.toTypedArray()
            serviceInfo = info
            Log.d(TAG, "updated accessibility package filter: $targets")
        } catch (e: Exception) {
            Log.w(TAG, "could not update package filter", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return

        // Check if there is an active detector for this package
        val platformDetector = Detectors.platformDetectorFor(pkg)
        val legacyDetector = if (platformDetector == null) Detectors.forPackage(pkg).firstOrNull() else null
        if (platformDetector == null && legacyDetector == null) return

        // Throttle tree reads: window-content events fire rapidly during video playback.
        val scanNow = SystemClock.elapsedRealtime()
        if (scanNow - lastScanAt < MIN_SCAN_MS) return
        lastScanAt = scanNow

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val ids = ArrayList<String>()
        val texts = ArrayList<String>()
        val descriptions = ArrayList<String>()
        read(root, ids, texts, descriptions)

        // 1. Process via universal VideoPlatformDetector
        if (platformDetector != null) {
            val snapshot = AccessibilitySnapshot(
                packageName = pkg,
                timestamp = System.currentTimeMillis(),
                viewIds = ids,
                textLabels = texts,
                contentDescriptions = descriptions
            )

            val observation = platformDetector.analyze(snapshot)
            if (observation == null) {
                lastFingerprint = null
                return
            }

            // Deduplication: same fingerprint = same video item
            val fingerprint = observation.contentKey
            if (fingerprint == lastFingerprint) return

            val now = System.currentTimeMillis()
            if (now - lastCountAt < MIN_GAP_MS) return
            lastFingerprint = fingerprint
            lastCountAt = now

            val contentObs = ContentObservation(
                packageName = pkg,
                timestamp = now,
                contentId = observation.contentKey,
                markers = snapshot.allMarkers
            )
            val outcome = DetectionOutcome(
                detected = true,
                contentKey = observation.contentKey,
                confidence = observation.confidence
            )

            Log.i(TAG, "counted a new $pkg ${observation.contentType.label} item")
            scope.launch {
                runCatching {
                    ContentRepository.record(contentObs, outcome, "accessibility:${platformDetector.javaClass.simpleName}")
                }.onFailure { Log.w(TAG, "could not store content observation", it) }
            }
            return
        }

        // 2. Fallback for legacy ContentDetector (e.g. DebugProbeDetector)
        if (legacyDetector != null) {
            val signals = ShortsSignals.analyse(ids, texts, descriptions)
            if (!signals.inShorts) {
                lastFingerprint = null
                return
            }

            val fingerprint = signals.fingerprint ?: NO_TEXT
            if (fingerprint == lastFingerprint) return

            val now = System.currentTimeMillis()
            if (now - lastCountAt < MIN_GAP_MS) return
            lastFingerprint = fingerprint
            lastCountAt = now

            val observation = ContentObservation(
                packageName = pkg,
                timestamp = now,
                contentId = "shorts:${ShortsSignals.hash(fingerprint)}:$now",
                markers = signals.markers
            )
            val outcome = legacyDetector.process(observation)
            if (!outcome.detected) return
            Log.i(TAG, "counted a new $pkg item via legacy detector")
            scope.launch {
                runCatching {
                    ContentRepository.record(observation, outcome, "accessibility:${legacyDetector.javaClass.simpleName}")
                }.onFailure { Log.w(TAG, "could not store content observation", it) }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Breadth-first walk of the active window; only labels and resource ids are collected in memory. */
    private fun read(
        root: AccessibilityNodeInfo,
        ids: MutableList<String>,
        texts: MutableList<String>,
        descriptions: MutableList<String>
    ) {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++
            node.viewIdResourceName?.substringAfterLast('/')?.let { ids.add(it) }
            node.text?.toString()?.let { texts.add(it) }
            node.contentDescription?.toString()?.let { descriptions.add(it) }
            if (depth >= MAX_DEPTH) continue
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it to depth + 1) }
            }
        }
    }

    companion object {
        private const val TAG = "ContentDetection"
        private const val MAX_NODES = 800
        private const val MAX_DEPTH = 30
        private const val MIN_SCAN_MS = 300L
        private const val MIN_GAP_MS = 3_000L
        private const val NO_TEXT = "no-text"
    }
}
