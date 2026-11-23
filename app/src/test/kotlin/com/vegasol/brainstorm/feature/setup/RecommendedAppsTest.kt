package com.vegasol.brainstorm.feature.setup

import com.vegasol.brainstorm.ui.Inst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedAppsTest {

    private fun entry(pkg: String, name: String, nameKey: String = RecommendedApps.normalizeName(name)) =
        RecommendedEntry(pkg, name, nameKey, listOf("SHORT_VIDEO"), "HIGH", "KNOWN_ID")

    @Test
    fun `package match is case insensitive and keeps the installed package`() {
        val installed = listOf(Inst("com.Example.Video", "Whatever"))
        val catalog = listOf(entry("com.example.video", "Example"))
        assertEquals(setOf("com.Example.Video"), RecommendedApps.match(installed, catalog))
    }

    @Test
    fun `name match ignores case, accents and punctuation`() {
        val installed = listOf(Inst("com.installed.app", "Jio  Hotstar!"))
        val catalog = listOf(entry("com.catalog.app", "JioHotstar"))
        assertEquals(setOf("com.installed.app"), RecommendedApps.match(installed, catalog))
    }

    @Test
    fun `ambiguous names are never used for name matching`() {
        val installed = listOf(Inst("com.installed.app", "Player"))
        val catalog = listOf(entry("com.a.app", "Player"), entry("com.b.app", "Player"))
        assertTrue(RecommendedApps.match(installed, catalog).isEmpty())
    }

    @Test
    fun `short generic names do not match and unrelated apps are left alone`() {
        val installed = listOf(Inst("com.installed.app", "TV"), Inst("com.other.app", "Banking"))
        val catalog = listOf(entry("com.catalog.app", "TV"), entry("com.video.app", "Video"))
        assertTrue(RecommendedApps.match(installed, catalog).isEmpty())
    }

    @Test
    fun `normalization strips accents and keeps letters and digits`() {
        assertEquals("cafevideo", RecommendedApps.normalizeName("Café Video!"))
        assertEquals("jiohotstar", RecommendedApps.normalizeName("JioHotstar"))
    }
}
