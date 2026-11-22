package com.vegasol.brainstorm

import com.vegasol.brainstorm.detect.ShortsSignals
import com.vegasol.brainstorm.detect.YouTubeShortsDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures mirror the labels YouTube's Shorts player exposes to the accessibility tree. */
class ContentDetectionTest {

    private val title = "probe short 1"
    private val channel = "probe channel"
    private val like = "like this video along with 1234 other people"

    private fun analyse(
        ids: List<String>,
        texts: List<String>,
        descriptions: List<String>,
        markers: List<String> = YouTubeShortsDetector.MARKERS
    ) = ShortsSignals.analyse(ids, texts, descriptions, markers)

    @Test
    fun `short player is recognised from a container id`() {
        val s = analyse(
            ids = listOf("reel_player", "bottom_bar_container"),
            texts = listOf(title),
            descriptions = emptyList()
        )
        assertTrue(s.inShorts)
        assertNotNull(s.fingerprint)
    }

    @Test
    fun `short player is recognised from the remix action label`() {
        val s = analyse(
            ids = emptyList(),
            texts = listOf(title),
            descriptions = listOf("Remix this video", like)
        )
        assertTrue(s.inShorts)
    }

    @Test
    fun `home screen nav label Shorts is not a false positive`() {
        val s = analyse(
            ids = listOf("bottom_bar_container"),
            texts = listOf("Home", "Shorts", "Subscriptions", "Library"),
            descriptions = listOf("Home", "Shorts", "Subscriptions", "Library")
        )
        assertFalse(s.inShorts)
    }

    @Test
    fun `engagement counters do not look like a new item`() {
        val first = analyse(
            ids = listOf("reel_player"),
            texts = listOf(title, channel, "1234 likes"),
            descriptions = listOf(like)
        )
        val liked = analyse(
            ids = listOf("reel_player"),
            texts = listOf(title, channel, "1235 likes"),
            descriptions = listOf("like this video along with 1235 other people")
        )
        assertEquals(first.fingerprint, liked.fingerprint)
    }

    @Test
    fun `formatted counters are ignored`() {
        val a = analyse(
            ids = listOf("reel_player"),
            texts = listOf(title, "1.2k likes", "34k views"),
            descriptions = emptyList()
        )
        val b = analyse(
            ids = listOf("reel_player"),
            texts = listOf(title, "1.3k likes", "35k views"),
            descriptions = emptyList()
        )
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test
    fun `a different short changes the fingerprint`() {
        val first = analyse(
            ids = listOf("reel_player"),
            texts = listOf(title, channel),
            descriptions = listOf(like)
        )
        val next = analyse(
            ids = listOf("reel_player"),
            texts = listOf("probe short 2", "another channel"),
            descriptions = listOf(like)
        )
        assertNotEquals(first.fingerprint, next.fingerprint)
    }

    @Test
    fun `no readable text leaves the fingerprint empty`() {
        val s = analyse(
            ids = listOf("reel_player"),
            texts = emptyList(),
            descriptions = listOf("Remix this video")
        )
        assertTrue(s.inShorts)
        assertNull(s.fingerprint)
    }

    @Test
    fun `markers from one app do not match another app's surface`() {
        val s = analyse(
            ids = listOf("reel_player"),
            texts = listOf(title),
            descriptions = emptyList(),
            markers = listOf("clips_viewer")
        )
        assertFalse(s.inShorts)
    }

    @Test
    fun `detector needs both a marker and a content key`() {
        val detector = YouTubeShortsDetector()
        val hit = detector.process(
            ContentObservation(
                packageName = YouTubeShortsDetector.YOUTUBE,
                timestamp = 1L,
                contentId = "shorts:abc:1",
                markers = setOf("remix this video")
            )
        )
        assertTrue(hit.detected)
        assertEquals("shorts:abc:1", hit.contentKey)
        assertEquals(YouTubeShortsDetector.CONFIDENCE, hit.confidence, 0.001f)

        val miss = detector.process(
            ContentObservation(
                packageName = YouTubeShortsDetector.YOUTUBE,
                timestamp = 1L,
                contentId = "shorts:abc:1",
                markers = setOf("home")
            )
        )
        assertFalse(miss.detected)
        assertNull(miss.contentKey)
    }

    @Test
    fun `only the YouTube package is registered`() {
        assertEquals(Support.EXPERIMENTAL, Detectors.support(YouTubeShortsDetector.YOUTUBE))
        assertEquals(Support.TIME_ONLY, Detectors.support("com.android.chrome"))
        assertEquals(1, Detectors.forPackage(YouTubeShortsDetector.YOUTUBE).size)
        assertTrue(Detectors.forPackage("com.android.chrome").isEmpty())
    }
}
