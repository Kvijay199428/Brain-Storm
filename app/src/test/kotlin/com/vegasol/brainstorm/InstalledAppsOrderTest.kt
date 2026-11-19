package com.vegasol.brainstorm

import com.vegasol.brainstorm.ui.AppCategory
import com.vegasol.brainstorm.ui.Inst
import com.vegasol.brainstorm.ui.matchApps
import com.vegasol.brainstorm.ui.orderForSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppsOrderTest {

    private fun inst(pkg: String, label: String, category: AppCategory) = Inst(pkg, label, category)

    @Test
    fun `detected social and video apps sort before other apps`() {
        val ordered = orderForSetup(
            listOf(
                inst("a.other", "Alpha", AppCategory.OTHER),
                inst("a.video", "Zeta", AppCategory.VIDEO),
                inst("a.social", "Beta", AppCategory.SOCIAL)
            )
        )
        assertEquals(listOf("a.social", "a.video", "a.other"), ordered.map { it.pkg })
    }

    @Test
    fun `alphabetical inside a group, case-insensitive`() {
        val ordered = orderForSetup(
            listOf(
                inst("a.2", "banana", AppCategory.OTHER),
                inst("a.1", "Apple", AppCategory.OTHER),
                inst("a.3", "cherry", AppCategory.OTHER)
            )
        )
        assertEquals(listOf("Apple", "banana", "cherry"), ordered.map { it.label })
    }

    @Test
    fun `only declared categories count as detected`() {
        assertTrue(inst("a", "A", AppCategory.SOCIAL).detected)
        assertTrue(inst("a", "A", AppCategory.VIDEO).detected)
        assertFalse(inst("a", "A", AppCategory.OTHER).detected)
    }

    // ------------------------------------ search filter ------------------------------------

    private val sample = listOf(
        inst("com.netflix.mediaclient", "Netflix", AppCategory.OTHER),
        inst("in.startv.hotstar", "JioHotstar", AppCategory.OTHER),
        inst("com.instagram.android", "Instagram", AppCategory.SOCIAL)
    )

    @Test
    fun `blank or whitespace query keeps the full list`() {
        assertEquals(sample, matchApps(sample, ""))
        assertEquals(sample, matchApps(sample, "   "))
    }

    @Test
    fun `query matches the visible label case-insensitively`() {
        assertEquals(listOf("com.netflix.mediaclient"), matchApps(sample, "NETFLIX").map { it.pkg })
        assertEquals(listOf("in.startv.hotstar"), matchApps(sample, "hotstar").map { it.pkg })
    }

    @Test
    fun `query also matches the package name`() {
        assertEquals(listOf("in.startv.hotstar"), matchApps(sample, "startv").map { it.pkg })
    }

    @Test
    fun `query without a match returns an empty list`() {
        assertTrue(matchApps(sample, "tiktok").isEmpty())
    }
}
