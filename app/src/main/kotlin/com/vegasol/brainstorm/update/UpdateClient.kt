package com.vegasol.brainstorm.update

import android.content.Context
import com.vegasol.brainstorm.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One installable release, already narrowed down to this app's channel. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val tag: String,
    val assetName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
    /** Package name recorded in `version.json`; null for a manifest written before schema 2. */
    val targetPackage: String?,
    /** Release page, used when the download cannot replace this install. */
    val pageUrl: String
)

sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck

    /**
     * A newer release exists but it is published under a different package name, so Android will
     * refuse to install it over this one. Offering the download would only end in a mismatch error,
     * so the UI sends the user to the release page instead.
     */
    data class NeedsReinstall(val info: UpdateInfo) : UpdateCheck

    data object UpToDate : UpdateCheck
    /** No answer is not the same as "you are current" — the UI must never claim otherwise. */
    data class Failed(val reason: String) : UpdateCheck
}

/**
 * Reads the public release list from GitHub. Unauthenticated requests are rate limited to 60/h per
 * IP, so [UpdateManager] only calls this once every few hours unless the user asks explicitly.
 *
 * Nothing here trusts the network: [download] verifies the SHA-256 from `version.json`, and
 * [ApkInstaller] then checks the signing certificate against the installed app before offering it.
 */
class UpdateClient(private val c: Context) {

    companion object {
        const val REPO = "Kvijay199428/Brain-Storm"
        private const val API = "https://api.github.com/repos/$REPO/releases?per_page=20"
    }

    private val channel: String
        get() = BuildConfig.CHANNEL.lowercase()

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val res = get(API)
            when {
                res.code == 403 || res.code == 429 ->
                    UpdateCheck.Failed("GitHub rate limit reached — try again later.")
                res.code != 200 -> UpdateCheck.Failed("GitHub answered HTTP ${res.code}.")
                else -> fromReleases(JSONArray(res.body))
            }
        } catch (e: IOException) {
            UpdateCheck.Failed("No connection to GitHub.")
        } catch (e: Exception) {
            UpdateCheck.Failed("Release list could not be read.")
        }
    }

    private fun fromReleases(releases: JSONArray): UpdateCheck {
        val wantsBeta = channel == "beta"
        val candidate = (0 until releases.length())
            .map { releases.getJSONObject(it) }
            .firstOrNull { r ->
                !r.optBoolean("draft") && (wantsBeta || !r.optBoolean("prerelease"))
            } ?: return UpdateCheck.UpToDate

        val info = parseRelease(candidate) ?: return UpdateCheck.UpToDate
        if (info.versionCode <= BuildConfig.VERSION_CODE) return UpdateCheck.UpToDate
        // A release whose package differs cannot replace this installation, whatever its version.
        val target = info.targetPackage
        return if (target != null && target != c.packageName) {
            UpdateCheck.NeedsReinstall(info)
        } else {
            UpdateCheck.Available(info)
        }
    }

    /**
     * A release is only usable if it carries a schema-2 `version.json` with one `apk` entry and a
     * hash. Anything less is ignored rather than guessed at.
     */
    private fun parseRelease(release: JSONObject): UpdateInfo? {
        val assets = release.optJSONArray("assets") ?: return null
        val versionAsset = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == "version.json" } ?: return null

        val manifest = get(versionAsset.optString("browser_download_url"))
        if (manifest.code != 200) return null
        val json = JSONObject(manifest.body)

        val apk = json.optJSONObject("apk") ?: return null
        val file = apk.optString("file").takeIf { it.isNotBlank() } ?: return null
        val sha = apk.optString("sha256").lowercase().takeIf { it.length == 64 } ?: return null

        val apkAsset = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == file } ?: return null

        return UpdateInfo(
            versionCode = json.optInt("versionCode"),
            versionName = json.optString("versionName", "?"),
            tag = json.optString("tag", release.optString("tag_name")),
            assetName = file,
            downloadUrl = apkAsset.optString("browser_download_url"),
            sha256 = sha,
            sizeBytes = apkAsset.optLong("size"),
            notes = release.optString("body", ""),
            targetPackage = json.optString("package").takeIf { it.isNotBlank() },
            pageUrl = release.optString("html_url")
        )
    }

    /** Streams the APK to the cache directory while hashing it; a short hash leaves no file behind. */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): java.io.File =
        withContext(Dispatchers.IO) {
            val dir = java.io.File(c.cacheDir, "updates").apply { mkdirs() }
            val tmp = java.io.File(dir, "${info.assetName}.part")
            val digest = MessageDigest.getInstance("SHA-256")
            val conn = open(info.downloadUrl)
            if (conn.responseCode != 200) throw IOException("Download failed: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val percent = ((read * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != info.sha256) {
                tmp.delete()
                throw IOException("Checksum mismatch — the file was discarded.")
            }
            val target = java.io.File(dir, info.assetName)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        }

    private class HttpResult(val code: Int, val body: String)

    private fun get(url: String): HttpResult {
        val conn = open(url)
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            HttpResult(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub rejects API calls without a User-Agent.
            setRequestProperty("User-Agent", "BrainStorm/${BuildConfig.VERSION_NAME}")
        }
}
