package com.netrik.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressParsingTest {

    @Test
    fun `SSID perde as aspas`() {
        assertEquals("Café-5G", cleanSsid("\"Café-5G\""))
    }

    @Test
    fun `SSID hidden by Android becomes null`() {
        assertNull(cleanSsid("<unknown ssid>"))
        assertNull(cleanSsid("\"\""))
    }

    @Test
    fun `public IP service reply is validated`() {
        assertTrue(looksLikeIpAddress("177.92.14.203"))
        assertTrue(looksLikeIpAddress("2804:14d:5c83:8a10::1f3a"))
        assertFalse(looksLikeIpAddress("<html>error</html>"))
        assertFalse(looksLikeIpAddress(""))
    }
}
