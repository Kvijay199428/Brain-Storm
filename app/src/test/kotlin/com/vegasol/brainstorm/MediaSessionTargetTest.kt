package com.vegasol.brainstorm

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSessionTargetTest {

    @Test
    fun `only playing packages with an active lockout are targeted`() {
        val playing = setOf("com.video.app", "com.music.app")
        val locked = setOf("com.video.app", "com.other.app")
        assertEquals(setOf("com.video.app"), MediaSessionMonitor.targets(playing, locked))
    }

    @Test
    fun `empty sides produce no targets`() {
        assertEquals(emptySet<String>(), MediaSessionMonitor.targets(emptySet(), setOf("a")))
        assertEquals(emptySet<String>(), MediaSessionMonitor.targets(setOf("a"), emptySet()))
    }
}
