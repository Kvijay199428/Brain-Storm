package com.vegasol.brainstorm

import android.app.admin.DevicePolicyManager
import android.content.Context

/**
 * The single enforcement entry point.
 *
 * Every release ships the Device Owner code path; it engages only when this package really is the
 * device owner, and otherwise falls back to [ConsumerEnforcement]. That is why one APK can serve
 * both a normal user and a provisioned device — and why an update can never hand a "managed"
 * install a "consumer" artefact, since there is only one artefact.
 */
object EnforcementProvider {

    fun create(c: Context): EnforcementController = ManagedEnforcement(c)

    /** True only when Android reports this package as the device owner. Never inferred from a build flag. */
    fun isDeviceOwner(c: Context): Boolean = runCatching {
        c.getSystemService(DevicePolicyManager::class.java)?.isDeviceOwnerApp(c.packageName) == true
    }.getOrDefault(false)

    /** Label for Settings. "Standard" when the app is not provisioned as owner. */
    fun edition(c: Context): String = if (isDeviceOwner(c)) "Device Owner" else "Standard"
}
