package com.netrik.core.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceClassifierTest {

    private fun kind(ports: Set<Int>, services: Set<String> = emptySet()) = DeviceClassifier.classify(ports, services)?.kind

    @Test
    fun `signature ports pick the kind`() {
        assertEquals(DeviceKind.Camera, kind(setOf(80, 554, 8000)))
        assertEquals(DeviceKind.Camera, kind(setOf(37777)))
        assertEquals(DeviceKind.Printer, kind(setOf(80, 443, 631, 9100)))
        assertEquals(DeviceKind.Speaker, kind(setOf(1400, 1443)))
        assertEquals(DeviceKind.Tv, kind(setOf(8060)))
        assertEquals(DeviceKind.Cast, kind(setOf(8008, 8009)))
        assertEquals(DeviceKind.Iphone, kind(setOf(62078)))
        assertEquals(DeviceKind.Router, kind(setOf(22, 80, 8291)))
        assertEquals(DeviceKind.VoipPhone, kind(setOf(80, 5060)))
        assertEquals(DeviceKind.SmartHome, kind(setOf(8123)))
        assertEquals(DeviceKind.Computer, kind(setOf(135, 139, 445)))
    }

    @Test
    fun `generic ports say nothing`() {
        assertNull(kind(setOf(22, 80, 443, 445, 139, 8080)))
        assertNull(kind(emptySet()))
    }

    @Test
    fun `synology needs both DSM ports`() {
        assertNull(kind(setOf(5000)))
        assertEquals(DeviceKind.Nas, kind(setOf(22, 139, 445, 5000, 5001)))
        assertEquals(DeviceKind.Nas, kind(setOf(445, 2049)))
    }

    @Test
    fun `order settles devices that match several rules`() {
        // Camera comes before printer.
        assertEquals(DeviceKind.Camera, kind(setOf(554, 9100)))
        // Sonos also announces AirPlay.
        assertEquals(DeviceKind.Speaker, kind(setOf(1400), setOf("_airplay._tcp", "_raop._tcp")))
        // Android TV also answers Cast.
        assertEquals(DeviceKind.Tv, kind(setOf(6466, 8008, 8009)))
        // Windows PC with a print server.
        assertEquals(DeviceKind.Printer, kind(setOf(135, 445, 515)))
    }

    @Test
    fun `mdns services count as evidence`() {
        val guess = DeviceClassifier.classify(emptySet(), setOf("_ipp._tcp", "_http._tcp"))
        assertEquals(DeviceKind.Printer, guess?.kind)
        assertEquals(KindEvidence.Service("_ipp._tcp"), guess?.evidence)
        assertEquals(DeviceKind.Computer, kind(emptySet(), setOf("_smb._tcp")))
        assertNull(kind(emptySet(), setOf("_http._tcp", "_ssh._tcp")))
    }

    @Test
    fun `evidence names the port`() {
        assertEquals(KindEvidence.Port(554), DeviceClassifier.classify(setOf(80, 554), emptySet())?.evidence)
        assertEquals(KindEvidence.Port(5000), DeviceClassifier.classify(setOf(5000, 5001), emptySet())?.evidence)
    }

    @Test
    fun `signature ports are checked beside the top 100`() {
        assertTrue(DeviceClassifier.SIGNATURE_PORTS.containsAll(listOf(554, 9100, 62078, 8291, 37777, 1400)))
    }

    @Test
    fun `device kind uses the quick check, not the sweep port`() {
        // The sweep also counts a refused connection: 62078 may well be closed.
        assertNull(LanDevice("192.168.0.9", Detection.Tcp(62078)).kind)
        assertEquals(DeviceKind.Iphone, LanDevice("192.168.0.9", Detection.Tcp(62078), openPorts = setOf(62078)).kind?.kind)
        val camera = null.merge(DeviceUpdate("192.168.0.10", detection = Detection.Icmp)).merge(DeviceUpdate("192.168.0.10", openPorts = setOf(80, 554)))
        assertEquals(DeviceKind.Camera, camera.kind?.kind)
    }

    @Test
    fun `open ports merge`() {
        val ip = "192.168.0.10"
        val found = null.merge(DeviceUpdate(ip, detection = Detection.Icmp))
        assertNull(found.openPorts)
        val checked = found.merge(DeviceUpdate(ip, openPorts = setOf(22)))
        assertEquals(setOf(22), checked.openPorts)
        // A later update without ports keeps them; another check adds to them.
        assertEquals(setOf(22), checked.merge(DeviceUpdate(ip, hostname = Sourced("nas", InfoSource.Dns))).openPorts)
        assertEquals(setOf(22, 80), checked.merge(DeviceUpdate(ip, openPorts = setOf(80))).openPorts)
        assertEquals(emptySet<Int>(), found.merge(DeviceUpdate(ip, openPorts = emptySet())).openPorts)
    }
}
