package com.netrik.feature.neighbors

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortVersionTest {
    @Test
    fun `RouterOS version keeps number and channel`() {
        assertEquals("7.14.3 (stable)", shortVersion("7.14.3 (stable) Apr/17/2024 08:12:22"))
        assertEquals("6.49.7", shortVersion("6.49.7"))
        assertEquals("7.15 (testing)", shortVersion("  7.15  (testing)  "))
    }
}
