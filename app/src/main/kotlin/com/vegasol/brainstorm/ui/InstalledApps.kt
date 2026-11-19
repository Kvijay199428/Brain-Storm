package com.vegasol.brainstorm.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * What Android says an app is.
 *
 * `ApplicationInfo.category` is filled from the app's own `android:appCategory` (or an installer
 * hint). It is the only Android-native classification API, so it is a hint, not a guarantee:
 * apps that declare nothing land in [OTHER] and stay manually selectable instead of being guessed.
 */
enum class AppCategory(val label: String, internal val rank: Int) {
    SOCIAL("social", 0),
    VIDEO("video", 1),
    OTHER("", 2)
}

data class Inst(
    val pkg: String,
    val label: String,
    val category: AppCategory = AppCategory.OTHER
) {
    /** True only when the app itself told Android it is a social or video app. */
    val detected: Boolean get() = category != AppCategory.OTHER
}

/** Detected apps first, then everything else; alphabetical inside each group. */
fun orderForSetup(apps: List<Inst>): List<Inst> =
    apps.sortedWith(compareBy({ it.category.rank }, { it.label.lowercase() }))

/**
 * Filter for the setup search field. Matches the visible name or the package, case-insensitively.
 * A blank query returns the list untouched, so the grouped view is never altered by an empty field.
 */
fun matchApps(apps: List<Inst>, query: String): List<Inst> {
    val q = query.trim()
    if (q.isEmpty()) return apps
    return apps.filter {
        it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true)
    }
}

fun installed(c: Context): List<Inst> {
    val pm = c.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return orderForSetup(
        pm.queryIntentActivities(i, 0)
            .mapNotNull { resolve ->
                val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                Inst(
                    pkg = info.packageName,
                    label = resolve.loadLabel(pm).toString(),
                    category = info.category.toAppCategory()
                )
            }
            .distinctBy { it.pkg }
            .filter { it.pkg != c.packageName }
    )
}

private fun Int.toAppCategory(): AppCategory = when (this) {
    ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
    ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
    else -> AppCategory.OTHER
}
