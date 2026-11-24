package com.vegasol.brainstorm

import android.app.AppOpsManager
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Read-only inspection of an installed app's PiP declaration and per-app PiP permission.
 * Nothing here changes another app's settings; the UI only guides the user to Android Settings.
 */
object PipCapabilityScanner {
    private val cache = ConcurrentHashMap<String, PipCapability>()

    fun scan(c: Context, pkg: String): PipCapability = cache[pkg] ?: compute(c, pkg).also { cache[pkg] = it }

    fun scanAll(c: Context, packages: Collection<String>): Map<String, PipCapability> =
        packages.associateWith { scan(c, it) }

    fun refresh(pkg: String) { cache.remove(pkg) }

    private fun compute(c: Context, pkg: String): PipCapability {
        // Android does not expose the manifest's supportsPictureInPicture flag through the public
        // PackageManager API. AppOps does expose the real per-app PiP access state, so we report
        // that honestly: an explicit allow/deny means PiP is in play, MODE_DEFAULT means unknown.
        val mode = runCatching {
            val uid = c.packageManager.getApplicationInfo(pkg, 0).uid
            val ops = c.getSystemService(AppOpsManager::class.java) ?: return@runCatching null
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, uid, pkg)
        }.getOrNull() ?: return PipCapability.UNKNOWN

        val supports = mode != AppOpsManager.MODE_DEFAULT
        val allowed = mode == AppOpsManager.MODE_ALLOWED
        return PipCapabilityRules.classify(supports, allowed)
    }
}
