package com.vegasol.brainstorm.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

sealed interface UpdateState {
    /** Nothing has been asked yet in this process. */
    data object Unknown : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val at: Long) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState

    /**
     * The published build uses a different package name. Downloading cannot work, so the only
     * honest action is to send the user to the release page for a one-time reinstall.
     */
    data class NeedsReinstall(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val at: Long, val message: String) : UpdateState
    data class InstallStarted(val outcome: InstallStart, val info: UpdateInfo) : UpdateState

    /** "Install unknown apps" is off for Brain Storm; the APK is verified and waiting. */
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
}

/**
 * Owns the update lifecycle: throttled check, verified download, install hand-off.
 *
 * Deliberately conservative — a 403, a timeout or a malformed release ends in [UpdateState.Failed],
 * never in "you are up to date". Only a 200 with a release that has a higher versionCode counts.
 */
class UpdateManager(c: Context) {

    companion object {
        private const val TAG = "Updates"
        private const val THROTTLE_MS = 6 * 60 * 60 * 1000L
        private const val KEY_LAST_CHECK = "lastCheck"
    }

    private val ctx = c.applicationContext
    private val prefs = ctx.getSharedPreferences(ApkInstaller.PREFS, Context.MODE_PRIVATE)
    private val client = UpdateClient(ctx)
    private val installer = ApkInstaller(ctx)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val busy = AtomicBoolean(false)

    /** Downloaded, verified APK kept between the permission prompt and the install. */
    private var pending: File? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = _state

    val lastChecked: Long get() = prefs.getLong(KEY_LAST_CHECK, 0L)

    /** Result text written by a silent install session, if any. */
    val lastInstallResult: String? get() = prefs.getString(ApkInstaller.KEY_LAST_INSTALL, null)

    fun clearInstallResult() = prefs.edit().remove(ApkInstaller.KEY_LAST_INSTALL).apply()

    /** Called on app start: one check per six hours unless the user presses the button. */
    fun maybeCheck() {
        val age = System.currentTimeMillis() - lastChecked
        if (age < THROTTLE_MS) return
        check()
    }

    fun check() {
        if (!busy.compareAndSet(false, true)) return
        _state.value = UpdateState.Checking
        scope.launch {
            try {
                val result = client.check()
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                _state.value = when (result) {
                    is UpdateCheck.Available -> UpdateState.Available(result.info)
                    is UpdateCheck.NeedsReinstall -> UpdateState.NeedsReinstall(result.info)
                    UpdateCheck.UpToDate -> UpdateState.UpToDate(now)
                    is UpdateCheck.Failed -> UpdateState.Failed(now, result.reason)
                }
            } finally {
                busy.set(false)
            }
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            try {
                _state.value = UpdateState.Downloading(info, 0)
                val apk = client.download(info) { percent ->
                    _state.value = UpdateState.Downloading(info, percent)
                }
                val problem = installer.verify(apk)
                if (problem != null) {
                    apk.delete()
                    _state.value = UpdateState.Failed(System.currentTimeMillis(), problem)
                    return@launch
                }
                _state.value = UpdateState.Ready(info, apk)
                pending = apk
                when (val start = installer.install(apk)) {
                    is InstallStart.Rejected ->
                        _state.value = UpdateState.Failed(System.currentTimeMillis(), start.reason)
                    InstallStart.NeedsInstallPermission ->
                        _state.value = UpdateState.NeedsPermission(info)
                    else -> {
                        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                        _state.value = UpdateState.InstallStarted(start, info)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "update failed", e)
                _state.value = UpdateState.Failed(
                    System.currentTimeMillis(), e.message ?: "The update could not be downloaded."
                )
            } finally {
                busy.set(false)
            }
        }
    }

    /**
     * Called when the screen resumes: if the user just granted "install unknown apps", continue
     * with the APK that was already downloaded and verified instead of downloading it again.
     */
    fun resumeAfterPermission() {
        val s = _state.value as? UpdateState.NeedsPermission ?: return
        if (!installer.canInstall()) return
        val apk = pending ?: return
        if (!apk.exists() || installer.verify(apk) != null) return
        if (!busy.compareAndSet(false, true)) return
        when (val start = installer.install(apk)) {
            is InstallStart.Rejected -> _state.value = UpdateState.Failed(System.currentTimeMillis(), start.reason)
            InstallStart.NeedsInstallPermission -> _state.value = UpdateState.NeedsPermission(s.info)
            else -> _state.value = UpdateState.InstallStarted(start, s.info)
        }
        busy.set(false)
    }

    fun openInstallPermissionSettings() = installer.openInstallPermissionSettings()

    /** Opens the GitHub release page in a browser; used when an in-place install is impossible. */
    fun openReleasePage(info: UpdateInfo) {
        val url = info.pageUrl.takeIf { it.startsWith("https://") } ?: return
        runCatching {
            ctx.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "could not open $url", it) }
    }
}
