package com.vegasol.brainstorm

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/**
 * Reads active media-session packages when the user has granted Notification access, and can
 * attempt to pause a locked app's session when the user separately opts in. A third party cannot
 * force a session to stay paused: the app may resume on its own. This is best effort, never
 * reported as guaranteed enforcement.
 */
class MediaSessionMonitor(private val c: Context) {
    private val manager: MediaSessionManager? = c.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(c, PlaybackNotificationListener::class.java)

    /** Packages actually playing (or buffering). */
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

    /** Best-effort pause of the given packages' active sessions. Returns packages we tried. */
    fun pause(packages: Set<String>): Set<String> {
        val m = manager ?: return emptySet()
        if (!Perms.notificationAccess(c) || packages.isEmpty()) return emptySet()
        val tried = LinkedHashSet<String>()
        runCatching {
            m.getActiveSessions(listener).forEach { controller ->
                if (controller.packageName in packages) {
                    runCatching { controller.transportControls.pause() }
                    tried += controller.packageName
                }
            }
        }
        return tried
    }

    companion object {
        /** Intersection helper kept pure so the targeting rule is unit-testable. */
        fun targets(playing: Set<String>, locked: Set<String>): Set<String> = playing intersect locked
    }
}
