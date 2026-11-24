package com.vegasol.brainstorm

/** Android PiP support and the per-app system permission are separate facts. */
enum class PipCapability {
    SUPPORTED_ALLOWED,
    SUPPORTED_DISABLED,
    NOT_SUPPORTED,
    UNKNOWN
}

object PipCapabilityRules {
    /** Pure classifier: [supportsPip] from package metadata, [allowed] from AppOps. */
    fun classify(supportsPip: Boolean?, allowed: Boolean?): PipCapability = when {
        supportsPip == null || allowed == null -> PipCapability.UNKNOWN
        !supportsPip -> PipCapability.NOT_SUPPORTED
        allowed -> PipCapability.SUPPORTED_ALLOWED
        else -> PipCapability.SUPPORTED_DISABLED
    }

    fun label(capability: PipCapability): String = when (capability) {
        PipCapability.SUPPORTED_ALLOWED -> "PiP supported · permission enabled"
        PipCapability.SUPPORTED_DISABLED -> "PiP supported · permission disabled"
        PipCapability.NOT_SUPPORTED -> "PiP support not confirmed"
        PipCapability.UNKNOWN -> "PiP state unknown"
    }
}
