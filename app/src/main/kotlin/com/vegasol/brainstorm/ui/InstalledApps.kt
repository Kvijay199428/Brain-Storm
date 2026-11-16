package com.vegasol.brainstorm.ui

import android.content.Context
import android.content.Intent

data class Inst(val pkg: String, val label: String)

/** Apps that usually account for the worst of it; shown first and pre-selected during setup. */
val SUGGESTED = setOf(
    "com.instagram.android",
    "com.google.android.youtube",
    "com.facebook.katana",
    "com.snapchat.android",
    "com.reddit.frontpage",
    "com.zhiliaoapp.musically",
    "com.mxtech.videoplayer.ad",
    "com.netflix.mediaclient"
)

fun installed(c: Context): List<Inst> {
    val pm = c.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { Inst(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .filter { it.pkg != c.packageName }
        .sortedBy { it.label.lowercase() }
        .sortedByDescending { it.pkg in SUGGESTED }
}
