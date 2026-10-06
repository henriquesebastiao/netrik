package com.netrik.core.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanLogicTest {

    @Test
    fun `range of a 24 network`() {
        val r = ScanRange.of("192.168.0.42", 24)!!
        assertEquals("192.168.0.0/24", r.cidr)
        assertEquals(254, r.hosts.size)
        assertEquals("192.168.0.1", r.hosts.first())
        assertEquals("192.168.0.254", r.hosts.last())
        assertFalse(r.truncated)
    }

    @Test
    fun `faixas pequenas e redes ponto a ponto`() {
        assertEquals(listOf("10.0.0.1", "10.0.0.2"), ScanRange.of("10.0.0.2", 30)!!.hosts)
        assertEquals(listOf("10.0.0.4", "10.0.0.5"), ScanRange.of("10.0.0.5", 31)!!.hosts)
        assertEquals(listOf("10.0.0.7"), ScanRange.of("10.0.0.7", 32)!!.hosts)
        assertEquals(1022, ScanRange.of("172.16.5.10", 22)!!.hosts.size)
    }

    @Test
    fun `large network scans only the device 24`() {
        val r = ScanRange.of("10.20.30.40", 16)!!
        assertTrue(r.truncated)
        assertEquals("10.20.30.0/24", r.cidr)
        assertEquals(254, r.hosts.size)
    }

    @Test
    fun `invalid inputs`() {
        assertNull(ScanRange.of("not an ip", 24))
        assertNull(ScanRange.of("10.0.0.1", 33))
    }

    @Test
    fun `NetBIOS query has the wildcard name encoded`() {
        val packet = Netbios.nodeStatusRequest(0x1234)
        assertEquals(50, packet.size)
        assertEquals(0x12, packet[0].toInt())
        assertEquals(0x34, packet[1].toInt())
        assertEquals(0x20, packet[12].toInt())
        // "*" (0x2A) becomes "CK"; 0x00 becomes "AA"
        assertEquals("CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", String(packet, 13, 32, Charsets.US_ASCII))
        assertEquals(0x21, packet[47].toInt()) // tipo NBSTAT
    }

    @Test
    fun `NetBIOS reply carries name, group and MAC`() {
        val status = Netbios.parseNodeStatus(nbstatResponse(mac = byteArrayOf(0x3C, 0x22, 0xFB.toByte(), 0x9A.toByte(), 0x10, 0x7E)))!!
        assertEquals("DESKTOP-ANA", status.computerName)
        assertEquals("WORKGROUP", status.workgroup)
        assertEquals("3C:22:FB:9A:10:7E", status.mac)
    }

    @Test
    fun `zeroed Samba MAC becomes unavailable`() {
        assertNull(Netbios.parseNodeStatus(nbstatResponse(mac = ByteArray(6)))!!.mac)
        assertNull(Netbios.parseNodeStatus(ByteArray(10)))
    }

    @Test
    fun `SSDP and UPnP description`() {
        val response = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://192.168.0.1:1900/rootDesc.xml\r\nST: upnp:rootdevice\r\n\r\n"
        assertEquals("http://192.168.0.1:1900/rootDesc.xml", Ssdp.location(response))
        assertNull(Ssdp.location("HTTP/1.1 200 OK\r\nLOCATION: file:///etc/passwd\r\n"))
        val xml = """<root><device><friendlyName>Living Room Router</friendlyName><manufacturer>TP-Link &amp; Co</manufacturer>
            <modelName>Archer C6</modelName></device></root>"""
        assertEquals(Ssdp.Description("Living Room Router", "TP-Link & Co", "Archer C6"), Ssdp.parseDescription(xml))
    }

    @Test
    fun `MAC and name announced via mDNS`() {
        assertEquals("3C:22:FB:9A:10:7E", MdnsText.macFrom("macbook-ana [3c:22:fb:9a:10:7e]", emptyMap()))
        assertEquals("AA:BB:CC:DD:EE:FF", MdnsText.macFrom("Sala", mapOf("deviceid" to "AA:BB:CC:DD:EE:FF")))
        assertNull(MdnsText.macFrom("HP LaserJet", mapOf("ty" to "HP LaserJet Pro")))
        assertEquals("macbook-ana", MdnsText.cleanName("macbook-ana [3c:22:fb:9a:10:7e]"))
    }

    @Test
    fun `merging sources keeps the best information`() {
        val ip = "192.168.0.23"
        val fromPing = null.merge(DeviceUpdate(ip, detection = Detection.Icmp, rttMs = 2.9))
        val withUpnp = fromPing.merge(DeviceUpdate(ip, hostname = Sourced("Living room NAS", InfoSource.Upnp), vendor = Sourced("Synology", InfoSource.Upnp)))
        val withDns = withUpnp.merge(DeviceUpdate(ip, hostname = Sourced("nas.lan", InfoSource.Dns)))
        val withMac = withDns.merge(
            DeviceUpdate(ip, mac = Sourced("00:11:32:8C:4D:10", InfoSource.Netbios), vendor = Sourced("Synology Incorporated", InfoSource.Oui), services = setOf("_smb._tcp")),
        )
        assertEquals(Detection.Icmp, withMac.detection)
        assertEquals(2.9, withMac.rttMs!!, 1e-9)
        assertEquals(Sourced("nas.lan", InfoSource.Dns), withMac.hostname)
        assertEquals(Sourced("Synology Incorporated", InfoSource.Oui), withMac.vendor)
        assertEquals("00:11:32:8C:4D:10", withMac.mac?.value)
        assertEquals(setOf("_smb._tcp"), withMac.services)

        // A worse name arriving later doesn't replace the better one.
        assertEquals("nas.lan", withMac.merge(DeviceUpdate(ip, hostname = Sourced("NAS", InfoSource.Netbios))).hostname?.value)
    }

    @Test
    fun `search and order by IP`() {
        val devices = listOf(
            LanDevice("192.168.0.23", Detection.Icmp, hostname = Sourced("nas.lan", InfoSource.Dns), mac = Sourced("00:11:32:8C:4D:10", InfoSource.Netbios), vendor = Sourced("Synology", InfoSource.Oui)),
            LanDevice("192.168.0.7", Detection.Icmp, vendor = Sourced("Dell", InfoSource.Upnp)),
            LanDevice("192.168.0.100", Detection.Tcp(80)),
        )
        assertTrue(devices[0].matches("nas"))
        assertTrue(devices[0].matches("00:11:32"))
        assertTrue(devices[0].matches("001132"))
        assertTrue(devices[0].matches("synology"))
        assertTrue(devices[1].matches("0.7"))
        assertFalse(devices[2].matches("dell"))

        assertEquals(listOf("192.168.0.7", "192.168.0.23", "192.168.0.100"), devices.sortedBy { it.ipValue }.map { it.ip })
    }

    /** Real NBSTAT reply in format: header, name, type 0x21, 2 names and MAC. */
    private fun nbstatResponse(mac: ByteArray): ByteArray {
        val header = byteArrayOf(0x12, 0x34, 0x84.toByte(), 0x00, 0, 0, 0, 1, 0, 0, 0, 0)
        val name = byteArrayOf(0x20) + "CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".toByteArray() + byteArrayOf(0)
        fun entry(n: String, suffix: Int, group: Boolean) =
            n.padEnd(15).toByteArray(Charsets.ISO_8859_1) + byteArrayOf(suffix.toByte(), if (group) 0x84.toByte() else 0x04, 0x00)
        val names = entry("DESKTOP-ANA", 0x00, false) + entry("WORKGROUP", 0x00, true)
        val rdata = byteArrayOf(2) + names + mac + ByteArray(40)
        val rr = byteArrayOf(0x00, 0x21, 0x00, 0x01, 0, 0, 0, 0, (rdata.size shr 8).toByte(), rdata.size.toByte())
        return header + name + rr + rdata
    }
}
