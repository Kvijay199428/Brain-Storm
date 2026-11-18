package com.vegasol.brainstorm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

/**
 * The hourly report is only honest if time is booked into the hour it was spent in. These tests
 * pin the boundary behaviour with a fixed zone so a DST change or the device locale cannot make
 * them flaky.
 */
class HourlyBucketsTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `interval inside one hour stays in that hour`() {
        val slices = UsageBuckets.slices(at("2026-11-17T10:10:00Z"), at("2026-11-17T10:50:00Z"), utc)
        assertEquals(1, slices.size)
        assertEquals(10, slices.single().hour)
        assertEquals(40 * 60_000L, slices.single().millis)
    }

    @Test
    fun `interval crossing an hour boundary is split`() {
        val slices = UsageBuckets.slices(at("2026-11-17T10:50:00Z"), at("2026-11-17T11:20:00Z"), utc)
        assertEquals(2, slices.size)
        assertEquals(listOf(10, 11), slices.map { it.hour })
        assertEquals(listOf(10 * 60_000L, 20 * 60_000L), slices.map { it.millis })
    }

    @Test
    fun `interval starting on the boundary belongs to the later hour`() {
        val slices = UsageBuckets.slices(at("2026-11-17T11:00:00Z"), at("2026-11-17T11:05:00Z"), utc)
        assertEquals(1, slices.size)
        assertEquals(11, slices.single().hour)
    }

    @Test
    fun `multi hour session produces one slice per hour`() {
        val slices = UsageBuckets.slices(at("2026-11-17T09:30:00Z"), at("2026-11-17T12:15:00Z"), utc)
        assertEquals(listOf(9, 10, 11, 12), slices.map { it.hour })
        assertEquals(
            listOf(30 * 60_000L, 60 * 60_000L, 60 * 60_000L, 15 * 60_000L),
            slices.map { it.millis }
        )
    }

    @Test
    fun `session ending exactly on a boundary does not open an empty slice`() {
        val slices = UsageBuckets.slices(at("2026-11-17T10:00:00Z"), at("2026-11-17T12:00:00Z"), utc)
        assertEquals(listOf(10, 11), slices.map { it.hour })
        assertTrue(slices.all { it.millis > 0L })
    }

    @Test
    fun `session that starts before midnight lands in both days`() {
        val slices = UsageBuckets.slices(at("2026-11-16T23:40:00Z"), at("2026-11-17T00:20:00Z"), utc)
        assertEquals(listOf(23, 0), slices.map { it.hour })
        assertEquals(listOf(20 * 60_000L, 20 * 60_000L), slices.map { it.millis })
    }

    @Test
    fun `empty and inverted intervals produce no slices`() {
        assertTrue(UsageBuckets.slices(at("2026-11-17T10:00:00Z"), at("2026-11-17T10:00:00Z"), utc).isEmpty())
        assertTrue(UsageBuckets.slices(at("2026-11-17T10:00:00Z"), at("2026-11-17T09:00:00Z"), utc).isEmpty())
    }

    @Test
    fun `slice durations always add up to the original interval`() {
        val start = at("2026-11-17T07:13:27Z")
        val end = at("2026-11-17T16:47:03Z")
        val slices = UsageBuckets.slices(start, end, utc)
        assertEquals(end - start, slices.sumOf { it.millis })
        assertEquals(start, slices.first().startMs)
        assertEquals(end, slices.last().endMs)
        slices.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
    }

    @Test
    fun `hour matches the slice start`() {
        UsageBuckets.slices(at("2026-11-17T07:13:27Z"), at("2026-11-17T09:00:00Z"), utc).forEach {
            assertEquals(UsageBuckets.hourOf(it.startMs, utc), it.hour)
        }
    }
}
