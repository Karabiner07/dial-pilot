package com.dialpilot.core.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetMatchTest {

    @Test
    fun `matches case-insensitive substring`() {
        assertTrue(HeadsetMonitor.matches(listOf("CMF Buds Pro 2"), "cmf"))
    }

    @Test
    fun `blank filter accepts any connected headset but not none`() {
        assertTrue(HeadsetMonitor.matches(listOf("Some Buds"), ""))
        assertFalse(HeadsetMonitor.matches(emptyList(), ""))
    }

    @Test
    fun `other headsets do not match`() {
        assertFalse(HeadsetMonitor.matches(listOf("Pixel Buds"), "CMF"))
    }
}
