package com.netrik.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddressTest {

    @Test
    fun `endereços de rede local`() {
        listOf("10.0.2.2", "172.16.0.1", "172.31.255.254", "192.168.0.42", "169.254.10.1", "fd12:3456::1", "fe80::1%wlan0").forEach {
            assertTrue(it, LocalAddress.isLocal(it))
        }
    }

    @Test
    fun `endereços públicos e inválidos`() {
        listOf("8.8.8.8", "172.32.0.1", "192.169.0.1", "100.64.0.1", "2804:14d::1", "google.com", "").forEach {
            assertFalse(it, LocalAddress.isLocal(it))
        }
    }
}
