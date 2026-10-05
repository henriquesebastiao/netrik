package com.netrik.core.wifi

import com.netrik.core.network.WifiBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiLogicTest {

    @Test
    fun `security from real capabilities`() {
        assertEquals(WifiSecurity.Wpa2, WifiSecurity.fromCapabilities("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.Wpa2Wpa3, WifiSecurity.fromCapabilities("[RSN-PSK+SAE-CCMP][ESS][MFPC]"))
        assertEquals(WifiSecurity.Wpa3, WifiSecurity.fromCapabilities("[RSN-SAE-CCMP][ESS][MFPR][MFPC]"))
        assertEquals(WifiSecurity.Owe, WifiSecurity.fromCapabilities("[RSN-OWE-CCMP][ESS][MFPR][MFPC]"))
        assertEquals(WifiSecurity.Enterprise, WifiSecurity.fromCapabilities("[WPA2-EAP/SHA1-CCMP][RSN-EAP/SHA1-CCMP][ESS]"))
        assertEquals(WifiSecurity.Wpa, WifiSecurity.fromCapabilities("[WPA-PSK-TKIP][ESS]"))
        assertEquals(WifiSecurity.Wep, WifiSecurity.fromCapabilities("[WEP][ESS]"))
        assertEquals(WifiSecurity.Open, WifiSecurity.fromCapabilities("[ESS]"))
        assertEquals(WifiSecurity.Open, WifiSecurity.fromCapabilities(""))
        assertTrue(WifiSecurity.Open.weak)
        assertFalse(WifiSecurity.Wpa3.weak)
    }

    @Test
    fun `quality bands from the design`() {
        assertEquals(SignalQuality.Excellent, SignalQuality.of(-48))
        assertEquals(SignalQuality.Excellent, SignalQuality.of(-60))
        assertEquals(SignalQuality.Good, SignalQuality.of(-61))
        assertEquals(SignalQuality.Good, SignalQuality.of(-70))
        assertEquals(SignalQuality.Weak, SignalQuality.of(-80))
        assertEquals(SignalQuality.VeryWeak, SignalQuality.of(-81))
    }

    @Test
    fun `channel width and center`() {
        assertEquals(20, WifiChannelWidth.toMhz(0))
        assertEquals(80, WifiChannelWidth.toMhz(2))
        assertEquals(160, WifiChannelWidth.toMhz(4))
        assertEquals(320, WifiChannelWidth.toMhz(5))
        // channel 36 at 80 MHz covers 36–48, center at 5210
        assertEquals(5210, WifiChannelWidth.center(5180, 80, 5210))
        assertEquals(5180, WifiChannelWidth.center(5180, 20, 0))
        assertEquals(5180, WifiChannelWidth.center(5180, 80, 0))
    }

    @Test
    fun `2_4 GHz axis`() {
        val axis = SpectrumAxis.forBand(WifiBand.GHz2_4)
        assertEquals(13, axis.ticks.size)
        // channel 6 (2437) at 20 MHz covers 2427–2447
        val (start, end) = axis.span(2437, 20)!!
        assertEquals(1.5f, start, 1e-4f)
        assertEquals(2.5f, end, 1e-4f)
    }

    @Test
    fun `5 GHz axis compresses the gaps between blocks`() {
        val axis = SpectrumAxis.forBand(WifiBand.GHz5)
        // 36–64 (8 canais) + 100–144 (12) + 149–177 (8)
        assertEquals(28f, axis.slots, 1e-4f)
        assertEquals(listOf(8f, 20f), axis.separators)
        assertEquals(0.5f, axis.position(5180.0)!!, 1e-4f) // canal 36
        assertEquals(8.5f, axis.position(5500.0)!!, 1e-4f) // canal 100
        assertNull(axis.position(5400.0)) // gap between UNII-2A and UNII-2C
        // 80 MHz centered at 5210 (36–48): 4 slots
        val (start, end) = axis.span(5210, 80)!!
        assertEquals(4f, end - start, 1e-4f)
    }

    @Test
    fun `6 GHz axis`() {
        val axis = SpectrumAxis.forBand(WifiBand.GHz6)
        assertEquals(59f, axis.slots, 1e-4f)
        assertEquals(1 to 0.5f, axis.ticks.first())
    }

    @Test
    fun `limit of 4 scans every 2 minutes`() {
        val t = ScanThrottle()
        repeat(4) { i ->
            assertTrue(t.canScan(i * 1_000L))
            t.record(i * 1_000L)
        }
        assertFalse(t.canScan(10_000))
        assertEquals(120_000L, t.nextAllowedAt(10_000))
        assertTrue(t.canScan(120_000))
    }

    @Test
    fun `scan refused by the system blocks until the window opens`() {
        val t = ScanThrottle()
        t.markRejected(5_000)
        assertFalse(t.canScan(6_000))
        assertTrue(t.canScan(125_000))
    }

    @Test
    fun `nearby list filters bands, drops the connected one and sorts`() {
        fun net(ssid: String?, bssid: String, dbm: Int, band: WifiBand, ch: Int, connected: Boolean = false) =
            WifiNetwork(ssid, bssid, dbm, 0, band, ch, 20, 0, WifiSecurity.Wpa2, connected)
        val all = listOf(
            net("Casa", "A", -50, WifiBand.GHz5, 36, connected = true),
            net("Vizinho", "B", -70, WifiBand.GHz2_4, 6),
            net(null, "C", -60, WifiBand.GHz2_4, 1),
            net("Alpha", "D", -80, WifiBand.GHz5, 149),
        )
        assertEquals(listOf("C", "B", "D"), all.nearby(WifiBand.entries.toSet(), WifiSort.Signal).map { it.bssid })
        assertEquals(listOf("C", "B", "D"), all.nearby(WifiBand.entries.toSet(), WifiSort.Channel).map { it.bssid })
        assertEquals(listOf("D", "B", "C"), all.nearby(WifiBand.entries.toSet(), WifiSort.Name).map { it.bssid })
        assertEquals(listOf("D"), all.nearby(setOf(WifiBand.GHz5), WifiSort.Signal).map { it.bssid })
    }

    @Test
    fun `hidden networks can be left out, except the connected one`() {
        fun net(ssid: String?, bssid: String, connected: Boolean = false) =
            WifiNetwork(ssid, bssid, -60, 0, WifiBand.GHz2_4, 6, 20, 0, WifiSecurity.Wpa2, connected)
        val all = listOf(net("Home", "A"), net(null, "B"), net(null, "C", connected = true))
        assertEquals(listOf("A", "B", "C"), all.withoutHidden(false).map { it.bssid })
        assertEquals(listOf("A", "C"), all.withoutHidden(true).map { it.bssid })
    }
}
