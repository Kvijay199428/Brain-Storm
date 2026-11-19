package com.vegasol.brainstorm

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BrainStormAdminReceiver : DeviceAdminReceiver()

/**
 * Device Owner enforcement. Every install carries this class; it only suspends packages when the
 * app really is the Device Owner, and otherwise delegates to the overlay shim.
 */
class ManagedEnforcement(private val c: Context) : EnforcementController {
    override val mode get() = if (owner()) "MANAGED" else "CONSUMER"
    private val fallback = ConsumerEnforcement(c)
    private val dpm = c.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(c, BrainStormAdminReceiver::class.java)

    private fun owner() = dpm.isDeviceOwnerApp(c.packageName)

    private fun protectedPkg(p: String): Boolean {
        val home = c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        return p == c.packageName || p == home || p in setOf(
            "android", "com.android.settings", "com.android.systemui", "com.android.phone", "com.android.dialer",
            "com.google.android.dialer", "com.android.packageinstaller", "com.google.android.permissioncontroller", "com.android.emergency"
        )
    }

    override suspend fun apply(l: Lockout): EnfResult {
        if (!owner()) return fallback.apply(l)
        if (protectedPkg(l.packageName)) return EnfResult(false, "protected system package")
        val failed = runCatching { dpm.setPackagesSuspended(admin, arrayOf(l.packageName), true) }.getOrNull()
            ?: return EnfResult(false, "suspend call failed")
        val ok = failed.isEmpty() && runCatching { dpm.isPackageSuspended(admin, l.packageName) }.getOrDefault(false)
        return if (ok) EnfResult(true, "package suspended") else EnfResult(false, "could not suspend package")
    }

    override suspend fun remove(l: Lockout): EnfResult {
        if (owner()) runCatching { dpm.setPackagesSuspended(admin, arrayOf(l.packageName), false) }
        return EnfResult(true, "")
    }

    override fun health() = if (owner()) Health.ACTIVE else fallback.health()
}
