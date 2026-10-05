package com.netrik.core.neighbor

import com.netrik.core.lan.Detection
import com.netrik.core.lan.InfoSource
import com.netrik.core.lan.LanDevice
import com.netrik.core.lan.Sourced
import com.netrik.core.lan.merge
import com.netrik.core.lan.toDeviceUpdate
import org.junit.Assert.assertEquals
import org.junit.Test

class NeighborListTest {

    @Test
    fun `a new announcement updates the device without losing fields`() {
        val first = Neighbor(NeighborProtocol.Mndp, "10.0.0.1", mac = "AA:BB:CC:00:00:01", identity = "R1", uptimeSeconds = 10, lastSeenMillis = 1)
        val later = Neighbor(NeighborProtocol.Mndp, "10.0.0.1", mac = "AA:BB:CC:00:00:01", uptimeSeconds = 70, lastSeenMillis = 61)
        val list = NeighborList.upsert(NeighborList.upsert(emptyList(), first), later)
        assertEquals(1, list.size)
        assertEquals("R1", list[0].identity)
        assertEquals(70L, list[0].uptimeSeconds)
        assertEquals(61L, list[0].lastSeenMillis)
    }

    @Test
    fun `same MAC on another protocol is another entry, sorted MikroTik first then by name`() {
        val list = listOf(
            Neighbor(NeighborProtocol.Ubiquiti, "10.0.0.5", mac = "AA:00:00:00:00:01", identity = "ap"),
            Neighbor(NeighborProtocol.Mndp, "10.0.0.3", mac = "AA:00:00:00:00:02"),
            Neighbor(NeighborProtocol.Mndp, "10.0.0.2", mac = "AA:00:00:00:00:03", identity = "beta"),
            Neighbor(NeighborProtocol.Mndp, "10.0.0.1", mac = "AA:00:00:00:00:04", identity = "Alpha"),
        ).fold(emptyList<Neighbor>(), NeighborList::upsert)
        assertEquals(listOf("Alpha", "beta", null, "ap"), NeighborList.sorted(list).map { it.identity })
    }

    @Test
    fun `an announcement fills name, MAC and model in the Devices scan`() {
        val neighbor = Neighbor(NeighborProtocol.Mndp, "10.0.0.1", mac = "AA:BB:CC:00:00:01", ipv4 = "10.0.0.1", identity = "Core", model = "RB5009")
        val device = (null as LanDevice?).merge(neighbor.toDeviceUpdate())
        assertEquals(Detection.Mndp, device.detection)
        assertEquals(Sourced("Core", InfoSource.Mndp), device.hostname)
        assertEquals(Sourced("AA:BB:CC:00:00:01", InfoSource.Mndp), device.mac)
        assertEquals("RB5009", device.model)
        // Reverse DNS still wins as the name; the MNDP identity beats mDNS.
        val withDns = device.merge(com.netrik.core.lan.DeviceUpdate("10.0.0.1", hostname = Sourced("core.lan", InfoSource.Dns)))
        assertEquals("core.lan", withDns.hostname?.value)
        val withMdns = device.merge(com.netrik.core.lan.DeviceUpdate("10.0.0.1", hostname = Sourced("core.local", InfoSource.Mdns)))
        assertEquals("Core", withMdns.hostname?.value)
    }
}
