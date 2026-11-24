package com.vegasol.brainstorm

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Observes active playback through the user-granted Notification listener. Reports PLAYING
 * transitions to the coordinator and can send bounded pause commands. It never claims that a
 * third-party app actually stopped playing unless its reported state confirms it.
 */
class MediaSessionMonitor(private val c: Context) {
    private val manager: MediaSessionManager? = c.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(c, PlaybackNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        syncCallbacks(controllers.orEmpty())
    }
    private var started = false

    private fun controllers(): List<MediaController> {
        val m = manager ?: return emptyList()
        if (!Perms.notificationAccess(c)) return emptyList()
        return runCatching { m.getActiveSessions(listener) }.getOrDefault(emptyList())
    }

    fun start() {
        val m = manager ?: return
        if (started || !Perms.notificationAccess(c)) return
        started = runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            syncCallbacks(controllers())
            true
        }.getOrDefault(false)
    }

    fun stop() {
        if (!started) return
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        callbacks.forEach { (ctrl, cb) -> runCatching { ctrl.unregisterCallback(cb) } }
        callbacks.clear()
        started = false
    }

    private fun syncCallbacks(active: List<MediaController>) {
        val gone = callbacks.keys - active.toSet()
        gone.forEach { ctrl ->
            callbacks.remove(ctrl)?.let { runCatching { ctrl.unregisterCallback(it) } }
        }
        active.forEach { ctrl ->
            if (ctrl !in callbacks) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (state?.state == PlaybackState.STATE_PLAYING) {
                            scope.launch { EnforcementCoordinator.onMediaPlaying(ctrl.packageName) }
                        }
                    }
                }
                runCatching { ctrl.registerCallback(cb) }
                callbacks[ctrl] = cb
            }
        }
    }

    fun isPlaying(pkg: String): Boolean = controllers().any { ctrl ->
        ctrl.packageName == pkg && when (ctrl.playbackState?.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> true
            else -> false
        }
    }

    fun playbackState(pkg: String): PlaybackState? = controllers().firstOrNull {
        it.packageName == pkg
    }?.playbackState

    /** Only request stop when the session advertises the action; otherwise pause is safer. */
    fun supportsStop(pkg: String): Boolean {
        val actions = playbackState(pkg)?.actions ?: return false
        return actions and PlaybackState.ACTION_STOP != 0L
    }

    fun stop(packages: Set<String>): Set<String> {
        val tried = LinkedHashSet<String>()
        controllers().forEach { ctrl ->
            if (ctrl.packageName in packages) {
                runCatching { ctrl.transportControls.stop() }
                tried += ctrl.packageName
            }
        }
        return tried
    }

    /** Sends a pause command; verification happens later through [isPlaying]. */
    fun pause(packages: Set<String>): Set<String> {
        val tried = LinkedHashSet<String>()
        controllers().forEach { ctrl ->
            if (ctrl.packageName in packages) {
                runCatching { ctrl.transportControls.pause() }
                tried += ctrl.packageName
            }
        }
        return tried
    }

    companion object {
        fun targets(playing: Set<String>, locked: Set<String>): Set<String> = playing intersect locked
    }
}
