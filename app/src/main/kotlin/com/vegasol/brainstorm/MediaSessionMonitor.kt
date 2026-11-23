package com.vegasol.brainstorm

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/** Reads active media-session packages when the user has granted Notification access. */
class MediaSessionMonitor(private val c: Context) {
    private val manager: MediaSessionManager? = c.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(c, PlaybackNotificationListener::class.java)

    /** Packages actually playing (or buffering). Detection only — no session can be paused here. */
    fun activePackages(): Set<String> {
        val m = manager ?: return emptySet()
        if (!Perms.notificationAccess(c)) return emptySet()
        return runCatching {
            m.getActiveSessions(listener)
                .filter { controller ->
                    val state = controller.playbackState?.state
                    state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                }
                .mapTo(LinkedHashSet<String>()) { it.packageName }
        }.getOrDefault(emptySet())
    }
}
