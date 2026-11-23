package com.vegasol.brainstorm

enum class CapabilityState { NOT_AVAILABLE, NOT_REQUESTED, REQUESTED, CONFIRMED, FAILED }

/** Per-capability, honest result of enforcing one lockout. Stored in Room v5 columns. */
data class EnforcementReport(
    val visualBlock: CapabilityState = CapabilityState.NOT_REQUESTED,
    val pipDetection: CapabilityState = CapabilityState.NOT_AVAILABLE,
    val playbackPause: CapabilityState = CapabilityState.NOT_AVAILABLE,
    val processTermination: CapabilityState = CapabilityState.NOT_AVAILABLE
) {
    /** Full enforcement requires every capability to be confirmed, including process control. */
    val overall: EnforcementState
        get() = if (visualBlock == CapabilityState.CONFIRMED &&
            pipDetection == CapabilityState.CONFIRMED &&
            playbackPause == CapabilityState.CONFIRMED &&
            processTermination == CapabilityState.CONFIRMED
        ) EnforcementState.ENFORCED else EnforcementState.PARTIAL

    fun summary(): String = buildList {
        if (visualBlock == CapabilityState.CONFIRMED) add("blocking active")
        if (pipDetection == CapabilityState.CONFIRMED) add("PiP detected")
        if (playbackPause == CapabilityState.CONFIRMED) add("playback paused")
        else if (playbackPause == CapabilityState.FAILED) add("playback pause failed")
        if (processTermination == CapabilityState.NOT_AVAILABLE) add("no process control on standard Android")
    }.joinToString(", ").replaceFirstChar { it.uppercase() }

    companion object {
        fun from(lock: Lockout): EnforcementReport = EnforcementReport(
            visualBlock = parse(lock.visualBlock),
            pipDetection = parse(lock.pipDetection),
            playbackPause = parse(lock.playbackPause),
            processTermination = parse(lock.processTermination)
        )

        private fun parse(value: String): CapabilityState =
            runCatching { CapabilityState.valueOf(value) }
                .getOrDefault(CapabilityState.NOT_AVAILABLE)
    }
}
