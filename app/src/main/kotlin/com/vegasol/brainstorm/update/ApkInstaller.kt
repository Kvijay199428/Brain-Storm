package com.vegasol.brainstorm.update

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

sealed interface InstallStart {
    /** Android's own installer is showing; the user still has to confirm. */
    data object SystemInstaller : InstallStart

    /** Device Owner only: a PackageInstaller session was committed without UI. */
    data object Silent : InstallStart

    /** "Install unknown apps" is off for Brain Storm; the caller must send the user to settings. */
    data object NeedsInstallPermission : InstallStart

    data class Rejected(val reason: String) : InstallStart
}

/**
 * Checks and hands a downloaded APK to Android.
 *
 * Two rules are non-negotiable: the APK must be signed by the same certificate as the running app
 * (so a hostile release in the repo cannot take over an install), and the app never claims a silent
 * update unless a Device Owner session really was committed.
 */
class ApkInstaller(private val c: Context) {

    companion object {
        private const val TAG = "Updates"
        private const val ACTION_RESULT = "com.vegasol.brainstorm.INSTALL_RESULT"
        const val PREFS = "updates"
        const val KEY_LAST_INSTALL = "lastInstallResult"
    }

    /** Returns null when [apk] is safe to install over this app, otherwise a human-readable reason. */
    fun verify(apk: File): String? {
        val archive = c.packageManager.getPackageArchiveInfo(
            apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: return "The downloaded file is not a valid APK."

        if (archive.packageName != c.packageName) {
            return "The download is for ${archive.packageName}, not ${c.packageName}."
        }

        val archiveSigners = archive.signingInfo?.apkContentsSigners?.map { fingerprint(it.toByteArray()) }.orEmpty()
        val installedSigners = c.packageManager
            .getPackageInfo(c.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners?.map { fingerprint(it.toByteArray()) }.orEmpty()

        if (archiveSigners.isEmpty() || installedSigners.isEmpty()) {
            return "The signing certificate of the download could not be read."
        }
        if (archiveSigners.none { it in installedSigners }) {
            return "The download is signed by a different key than this installation — it cannot update it."
        }
        return null
    }

    fun install(apk: File): InstallStart {
        if (!c.packageManager.canRequestPackageInstalls()) return InstallStart.NeedsInstallPermission

        if (isDeviceOwner()) {
            // A Device Owner may commit its own update session without UI. If the platform refuses,
            // fall through to the normal installer instead of pretending the update happened.
            runCatching { commitSilently(apk) }
                .onSuccess { return InstallStart.Silent }
                .onFailure { Log.w(TAG, "silent install refused, using system installer", it) }
        }
        return launchSystemInstaller(apk)
    }

    fun openInstallPermissionSettings() {
        c.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun isDeviceOwner(): Boolean = runCatching {
        val dpm = c.getSystemService(DevicePolicyManager::class.java)
        dpm != null && dpm.isDeviceOwnerApp(c.packageName)
    }.getOrDefault(false)

    private fun launchSystemInstaller(apk: File): InstallStart = try {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.updates", apk)
        c.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        InstallStart.SystemInstaller
    } catch (e: Exception) {
        InstallStart.Rejected("Android could not open the installer: ${e.message ?: e::class.simpleName}")
    }

    private fun commitSilently(apk: File) {
        val installer = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("brainstorm", 0, apk.length()).use { out -> input.copyTo(out) }
            }
            val result = Intent(c, UpdateInstallReceiver::class.java).setAction(ACTION_RESULT)
            val pending = PendingIntent.getBroadcast(
                c, sessionId, result,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            session.commit(pending.intentSender)
        }
    }

    private fun fingerprint(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** Records the outcome of a silent session so Settings can tell the truth after the restart. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        val text = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Android needs your confirmation."
            PackageInstaller.STATUS_SUCCESS -> "Update installed."
            else -> "Install failed: $message"
        }
        context.getSharedPreferences(ApkInstaller.PREFS, Context.MODE_PRIVATE)
            .edit().putString(ApkInstaller.KEY_LAST_INSTALL, text).apply()
        Log.i("Updates", "install result: $text")
    }
}
