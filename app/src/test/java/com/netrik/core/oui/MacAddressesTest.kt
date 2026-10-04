package com.netrik.core.oui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacAddressesTest {

    @Test
    fun `aceita os formatos comuns e normaliza`() {
        val expected = MacInput.Full("3C22FB9A107E")
        assertEquals(expected, MacAddresses.parse("3C:22:FB:9A:10:7E"))
        assertEquals(expected, MacAddresses.parse("3c-22-fb-9a-10-7e"))
        assertEquals(expected, MacAddresses.parse("3C22.FB9A.107E"))
        assertEquals(expected, MacAddresses.parse("3c22fb9a107e"))
        assertEquals(expected, MacAddresses.parse("  3C 22 FB 9A 10 7E  "))
        assertEquals("3C:22:FB:9A:10:7E", MacAddresses.normalize("3c22.fb9a.107e"))
    }

    @Test
    fun `prefixos parciais`() {
        assertEquals(MacInput.Prefix("001132"), MacAddresses.parse("00-11-32"))
        assertEquals(MacInput.Prefix("240AC45"), MacAddresses.parse("24-0a-c4-5"))
        assertEquals(MacInput.Partial("240AC"), MacAddresses.parse("24:0A:C"))
        assertTrue(MacAddresses.parse("00-11-32").isQueryable)
        assertFalse(MacAddresses.parse("00-11").isQueryable)
        assertNull(MacAddresses.normalize("00-11-32"))
    }

    @Test
    fun `invalid inputs`() {
        assertEquals(MacInput.Empty, MacAddresses.parse("   "))
        assertEquals(MacInput.InvalidChar("3C22", 'G'), MacAddresses.parse("3C:22:G"))
        assertEquals(MacInput.TooLong("3C22FB9A107E"), MacAddresses.parse("3C:22:FB:9A:10:7E:01"))
        assertFalse(MacAddresses.parse("3C:22:Z").isQueryable)
    }

    @Test
    fun `formatting by octets`() {
        assertEquals("3C:22:FB:9A:10:7E", MacAddresses.format("3C22FB9A107E"))
        assertEquals("C8:5C:E2:7", MacAddresses.format("C85CE27"))
        assertEquals("", MacAddresses.format(""))
    }

    @Test
    fun `locally administered bit (random MAC)`() {
        // 2nd digit 2, 6, A or E
        listOf("DAA1196E035C", "02420000AC11", "F60000", "AE0000").forEach {
            assertTrue(it, MacAddresses.isLocallyAdministered(it))
        }
        listOf("3C22FB9A107E", "001132", "C006C3").forEach {
            assertFalse(it, MacAddresses.isLocallyAdministered(it))
        }
    }

    @Test
    fun `multicast bit`() {
        assertTrue(MacAddresses.isMulticast("01005E000001"))
        assertTrue(MacAddresses.isMulticast("333300000001"))
        assertFalse(MacAddresses.isMulticast("3C22FB9A107E"))
    }
}
