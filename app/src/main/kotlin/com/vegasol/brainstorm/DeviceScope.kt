package com.vegasol.brainstorm

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Which installed apps count toward whole-device usage.
 *
 * The scope is deliberately narrow: an app the user installed and can open, plus anything Brain
 * Storm monitors, so a preinstalled YouTube or Netflix picked during setup still appears. Launchers
 * are the home surface rather than something the user "spends time in", so they stay out of the
 * ranking; so does the system UI. Brain Storm excludes itself before it ever gets here.
 */
object DeviceScope {
    const val SYSTEM_UI = "com.android.systemui"

    data class Candidate(
        val pkg: String,
        val system: Boolean,
        val launcher: Boolean,
        val home: Boolean = false,
        val monitored: Boolean = false
    )

    fun include(c: Candidate): Boolean =
        c.monitored || (c.launcher && !c.system && !c.home && c.pkg != SYSTEM_UI)
}

/**
 * Resolves package -> label for usage recording and reports.
 *
 * Launcher apps are queried at most once a minute: the reconcile loop runs every few seconds and
 * the installed set barely changes, but the monitored apps are merged on every call so a freshly
 * added app is recorded immediately.
 */
object DeviceInventory {
    private const val CACHE_MS = 60_000L
    private data class Snapshot(val at: Long, val apps: Map<String, String>)

    @Volatile private var snapshot: Snapshot? = null

    /** Every user-installed app with a launcher entry, labelled, excluding this app and launchers. */
    fun userApps(c: Context, now: Long = System.currentTimeMillis()): Map<String, String> {
        snapshot?.let { if (now - it.at <= CACHE_MS) return it.apps }
        val pm = c.packageManager
        val home = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).mapNotNull { it.activityInfo?.packageName }.toSet()

        val apps = LinkedHashMap<String, String>()
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).forEach { resolve ->
            val info = resolve.activityInfo?.applicationInfo ?: return@forEach
            if (info.packageName == c.packageName) return@forEach
            val candidate = DeviceScope.Candidate(
                pkg = info.packageName,
                system = (info.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0,
                launcher = true,
                home = info.packageName in home
            )
            if (DeviceScope.include(candidate)) {
                apps[info.packageName] = resolve.loadLabel(pm).toString()
            }
        }
        snapshot = Snapshot(now, apps)
        return apps
    }

    /** User-installed apps plus every monitored app, labelled. Monitored labels win. */
    fun labels(
        c: Context,
        monitored: List<MonitoredApp>,
        now: Long = System.currentTimeMillis()
    ): Map<String, String> {
        val out = LinkedHashMap(userApps(c, now))
        monitored.forEach { out[it.packageName] = it.appName }
        return out
    }
}
