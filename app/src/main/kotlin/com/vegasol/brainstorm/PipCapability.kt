package com.vegasol.brainstorm

/** Android's per-app PiP access state. MODE_DEFAULT is not proof of support or denial. */
enum class PipCapability {
    SUPPORTED_ALLOWED,
    SUPPORTED_DISABLED,
    DEFAULT_ALLOWED,
    UNKNOWN
}

/** Platform-independent mirror of the relevant AppOps modes. */
enum class PipOpMode { ALLOWED, DENIED, DEFAULT, UNKNOWN }

object PipCapabilityRules {
    fun classify(mode: PipOpMode): PipCapability = when (mode) {
        PipOpMode.ALLOWED -> PipCapability.SUPPORTED_ALLOWED
        PipOpMode.DENIED -> PipCapability.SUPPORTED_DISABLED
        PipOpMode.DEFAULT -> PipCapability.DEFAULT_ALLOWED
        PipOpMode.UNKNOWN -> PipCapability.UNKNOWN
    }

    fun label(capability: PipCapability): String = when (capability) {
        PipCapability.SUPPORTED_ALLOWED -> "PiP supported · permission enabled"
        PipCapability.SUPPORTED_DISABLED -> "PiP supported · permission disabled"
        PipCapability.DEFAULT_ALLOWED -> "PiP default-allowed · support unconfirmed"
        PipCapability.UNKNOWN -> "PiP state unknown"
    }
}
