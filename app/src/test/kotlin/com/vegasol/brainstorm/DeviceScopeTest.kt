package com.vegasol.brainstorm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceScopeTest {

    private fun candidate(
        pkg: String,
        system: Boolean = false,
        launcher: Boolean = true,
        home: Boolean = false,
        monitored: Boolean = false
    ) = DeviceScope.Candidate(pkg, system, launcher, home, monitored)

    @Test
    fun `a user installed launcher app is included`() {
        assertTrue(DeviceScope.include(candidate("com.example.notes")))
    }

    @Test
    fun `a preinstalled app nobody monitors stays out`() {
        assertFalse(DeviceScope.include(candidate("com.android.settings", system = true)))
    }

    @Test
    fun `a preinstalled app that is monitored is included`() {
        assertTrue(
            DeviceScope.include(candidate("com.google.android.youtube", system = true, monitored = true))
        )
    }

    @Test
    fun `a monitored app without a launcher is still recorded`() {
        assertTrue(DeviceScope.include(candidate("com.example.service", launcher = false, monitored = true)))
    }

    @Test
    fun `apps without a launcher entry are not part of the device ranking`() {
        assertFalse(DeviceScope.include(candidate("com.example.sync", launcher = false)))
    }

    @Test
    fun `launchers are excluded even when the user installed them`() {
        assertFalse(DeviceScope.include(candidate("com.example.launcher", home = true)))
    }

    @Test
    fun `the system ui is never included`() {
        assertFalse(DeviceScope.include(candidate(DeviceScope.SYSTEM_UI)))
    }
}
