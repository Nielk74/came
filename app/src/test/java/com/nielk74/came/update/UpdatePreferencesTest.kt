package com.nielk74.came.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePreferencesTest {
    @Test
    fun `automatic check is due initially and after 24 hours`() {
        val interval = UpdatePreferences.AutomaticCheckIntervalMillis

        assertTrue(UpdatePreferences.isAutomaticCheckDue(0L, 1_000L))
        assertFalse(UpdatePreferences.isAutomaticCheckDue(1_000L, 1_000L + interval - 1L))
        assertTrue(UpdatePreferences.isAutomaticCheckDue(1_000L, 1_000L + interval))
    }

    @Test
    fun `future timestamps do not trigger repeated checks`() {
        assertFalse(UpdatePreferences.isAutomaticCheckDue(10_000L, 9_000L))
    }
}
