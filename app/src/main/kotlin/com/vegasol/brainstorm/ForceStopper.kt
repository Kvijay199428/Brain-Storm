package com.vegasol.brainstorm

import android.content.Context

/**
 * EXPERIMENTAL / NOT SUPPORTED.
 *
 * Attempts to call the hidden ActivityManager.forceStopPackage() API through reflection. Android 9+
 * restrict non-system apps from reaching hidden APIs, so this will normally fail; it is never
 * reported as successful enforcement unless the call actually returned. It is also outside Google
 * Play's permitted use of the platform and must not ship in a Play build.
 */
object ForceStopper {
    fun forceStop(context: Context, pkg: String): Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE)
            ?: return@runCatching false
        val method = runCatching { am.javaClass.getMethod("forceStopPackage", String::class.java) }
            .getOrElse { am.javaClass.getDeclaredMethod("forceStopPackage", String::class.java) }
        method.isAccessible = true
        method.invoke(am, pkg)
        true
    }.getOrDefault(false)
}
