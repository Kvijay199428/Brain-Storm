package com.vegasol.brainstorm.detect

import com.vegasol.brainstorm.BuildConfig
import com.vegasol.brainstorm.ContentDetector
import com.vegasol.brainstorm.ContentObservation
import com.vegasol.brainstorm.DetectionOutcome
import com.vegasol.brainstorm.Support

/**
 * Debug builds only. Registers the probe activity as an experimental surface so the dashboard
 * and app-detail screen render it exactly like a real detector. Never registered in release
 * builds: `Detectors` only adds it when `BuildConfig.DEBUG` is true.
 */
class DebugProbeDetector : ContentDetector {
    private val delegate = YouTubeShortsDetector()
    override val packages: Set<String> = setOf(BuildConfig.APPLICATION_ID)
    override val support: Support = Support.EXPERIMENTAL
    override fun process(o: ContentObservation): DetectionOutcome = delegate.process(o)
}
