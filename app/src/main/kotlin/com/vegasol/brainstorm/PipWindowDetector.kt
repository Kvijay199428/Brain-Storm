package com.vegasol.brainstorm

/**
 * Pure PiP heuristic. A restricted package is only treated as PiP when it is visible, not an
 * accessibility overlay, and its bounds are small relative to the screen. Size alone is never
 * enough — the package identity must match an active lockout first.
 */
object PipWindowDetector {
    data class Window(
        val packageName: String?,
        val visible: Boolean,
        val overlay: Boolean,
        val width: Int,
        val height: Int
    )

    fun isRestrictedPip(
        window: Window,
        restrictedPackages: Set<String>,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val pkg = window.packageName ?: return false
        return pkg in restrictedPackages &&
            window.visible &&
            !window.overlay &&
            window.width > 0 && window.height > 0 &&
            window.width < screenWidth * 0.6f &&
            window.height < screenHeight * 0.6f
    }
}
