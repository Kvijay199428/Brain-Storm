package com.vegasol.brainstorm.feature.setup

import android.content.Context
import com.vegasol.brainstorm.ui.Inst
import org.json.JSONObject
import java.text.Normalizer

/** One entry of the catalog shipped in assets/recommended_apps.json. */
data class RecommendedEntry(
    val packageName: String,
    val name: String,
    val nameKey: String,
    val categories: List<String>,
    val priority: String,
    val status: String
)

/** Reads the app-shipped catalog. Kept separate from the matcher so the rules stay unit-testable. */
object RecommendedCatalog {
    fun load(context: Context): List<RecommendedEntry> {
        val text = context.assets.open("recommended_apps.json").bufferedReader().use { it.readText() }
        val array = JSONObject(text).getJSONArray("apps")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val cats = o.optJSONArray("categories")
            RecommendedEntry(
                packageName = o.getString("package"),
                name = o.getString("name"),
                nameKey = o.optString("nameKey"),
                categories = if (cats == null) emptyList() else (0 until cats.length()).map { c -> cats.getString(c) },
                priority = o.optString("priority"),
                status = o.optString("status")
            )
        }
    }
}

/**
 * Case-insensitive matching between installed apps and the shipped catalog. Exact package wins;
 * a normalized app name is a fallback only when that name identifies a single catalog entry, so a
 * generic name can never silently tick an unrelated app.
 */
object RecommendedApps {
    /** Same normalization the CSV converter uses: lowercase, strip accents and punctuation. */
    fun normalizeName(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD).lowercase()
        val out = StringBuilder()
        decomposed.forEach { ch ->
            val type = Character.getType(ch)
            if (type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            ) return@forEach
            if (ch.isLetterOrDigit()) out.append(ch)
        }
        return out.toString()
    }

    /** Packages of the installed apps that match the catalog; display always uses the installed app. */
    fun match(installed: List<Inst>, catalog: List<RecommendedEntry>): Set<String> {
        val packages = catalog.mapTo(HashSet()) { it.packageName.lowercase() }
        val nameCounts = catalog.groupingBy { it.nameKey }.eachCount()
        val unambiguousNames = catalog
            .filter { nameCounts[it.nameKey] == 1 }
            .associate { it.nameKey to it.packageName }

        val matched = LinkedHashSet<String>()
        installed.forEach { app ->
            if (app.pkg.lowercase() in packages) {
                matched += app.pkg
            } else {
                val key = normalizeName(app.label)
                if (key.length >= 3 && unambiguousNames.containsKey(key)) matched += app.pkg
            }
        }
        return matched
    }
}
