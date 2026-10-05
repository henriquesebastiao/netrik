package com.netrik.core.portscan

import com.netrik.core.lan.ReachabilityProber
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class PortScanLogicTest {

    @Test
    fun `port list with singles and ranges`() {
        val list = PortList.parse("22, 80,443,8000-8100") as PortList.Valid
        assertEquals(104, list.ports.size)
        assertEquals(3, list.singles)
        assertEquals(1, list.ranges)
        assertEquals(22, list.ports.first())
        assertEquals(8100, list.ports.last())
    }

    @Test
    fun `repeated ports count once and come out sorted`() {
        val list = PortList.parse("443,22,22,20-23") as PortList.Valid
        assertEquals(listOf(20, 21, 22, 23, 443), list.ports)
    }

    @Test
    fun `port list errors`() {
        assertEquals(PortList.Empty, PortList.parse(" , "))
        assertEquals(PortList.Malformed("ssh"), PortList.parse("22,ssh"))
        assertEquals(PortList.Malformed("80-"), PortList.parse("80-"))
        assertEquals(PortList.OutOfRange("0"), PortList.parse("0"))
        assertEquals(PortList.OutOfRange("70000"), PortList.parse("70000"))
        assertEquals(PortList.OutOfRange("9000-8000"), PortList.parse("9000-8000"))
    }

    @Test
    fun `CIDR network of Network mode`() {
        assertEquals(Cidr.Result.Valid("192.168.0.0", 24), Cidr.parse("192.168.0.0/24"))
        assertEquals(Cidr.Result.Valid("10.0.0.5", 22), Cidr.parse(" 10.0.0.5/22 "))
        assertEquals(Cidr.Result.TooLarge, Cidr.parse("10.0.0.0/16"))
        assertEquals(Cidr.Result.Malformed, Cidr.parse("192.168.0.0"))
        assertEquals(Cidr.Result.Malformed, Cidr.parse("192.168.0/24"))
        assertEquals(Cidr.Result.Malformed, Cidr.parse("192.168.0.0/40"))
    }

    @Test
    fun `worst case estimate`() {
        // 100 TCP ports, 128 in parallel, 1 s timeout: one batch.
        assertEquals(1, ScanEstimate.seconds(100, Protocol.Tcp, 1000))
        // 254 × 1000 TCP: 1985 batches of 1 s.
        assertEquals(1985, ScanEstimate.seconds(254_000, Protocol.Tcp, 1000))
        // UDP with 16 in parallel and 2 s.
        assertEquals(14, ScanEstimate.seconds(100, Protocol.Udp, 2000))
        assertEquals(0, ScanEstimate.seconds(0, Protocol.Tcp, 1000))
    }

    @Test
    fun `sondas UDP por protocolo`() {
        val dns = UdpProbes.payloadFor(53)
        assertEquals(17, dns.size)
        assertEquals(1, dns[5].toInt()) // 1 pergunta
        val ntp = UdpProbes.payloadFor(123)
        assertEquals(48, ntp.size)
        assertEquals(0x1B, ntp[0].toInt())
        val snmp = UdpProbes.payloadFor(161)
        assertEquals(0x30, snmp[0].toInt())
        assertEquals(snmp.size - 2, snmp[1].toInt()) // ASN.1 message length
        assertEquals(50, UdpProbes.payloadFor(137).size) // NBSTAT
        assertEquals(0, UdpProbes.payloadFor(9999).size)
    }

    @Test
    fun `lists and names bundled in the APK`() {
        val dir = File("src/main/assets/ports")
        val tcp = File(dir, "top-tcp.txt").reader().use(PortCatalogFiles::parseTop)
        val udp = File(dir, "top-udp.txt").reader().use(PortCatalogFiles::parseTop)
        assertEquals(1000, tcp.size)
        assertEquals(1000, udp.size)
        assertEquals(1000, tcp.toSet().size)
        assertEquals(listOf(80, 23, 443, 21, 22), tcp.take(5))
        assertTrue(53 in udp.take(10))
        val names = GZIPInputStream(File(dir, "services.tsv.gzip").inputStream()).bufferedReader().use(PortCatalogFiles::parseServices)
        assertEquals("ssh", names.getValue(Protocol.Tcp)[22])
        assertEquals("http", names.getValue(Protocol.Tcp)[80])
        assertEquals("domain", names.getValue(Protocol.Udp)[53])
    }

    private class FakeProber(private val open: Set<Pair<String, Int>>, private val closed: Set<Pair<String, Int>>) : PortProber {
        override suspend fun tcp(ip: String, port: Int, timeoutMs: Int) = when (ip to port) {
            in open -> PortState.Open
            in closed -> PortState.Closed
            else -> PortState.Filtered
        }
        override suspend fun udp(ip: String, port: Int, timeoutMs: Int) = PortState.OpenFiltered
    }

    @Test
    fun `single host scanner reports each port`() = runTest {
        val prober = FakeProber(open = setOf("10.0.0.7" to 22), closed = setOf("10.0.0.7" to 80))
        val hostProber = ReachabilityProber(object : com.netrik.core.network.ping.PingRunner {
            override fun run(command: List<String>) = kotlinx.coroutines.flow.emptyFlow<com.netrik.core.network.ping.PingEvent>()
        }, StandardTestDispatcher(testScheduler))
        val events = PortScanner(prober, hostProber, { _, _, _ -> null }).scan(listOf("10.0.0.7"), listOf(22, 80, 443), Protocol.Tcp, 500, discoverFirst = false).toList()

        val ports = events.filterIsInstance<PortScanEvent.Port>().associate { it.port to it.state }
        assertEquals(mapOf(22 to PortState.Open, 80 to PortState.Closed, 443 to PortState.Filtered), ports)
        val last = events.filterIsInstance<PortScanEvent.Progress>().last()
        assertEquals(3, last.checksDone)
        assertEquals(3, last.checksTotal)
        assertEquals(1, last.hostsDone)
    }

    @Test
    fun `UDP without reply stays open or filtered`() = runTest {
        val hostProber = ReachabilityProber(object : com.netrik.core.network.ping.PingRunner {
            override fun run(command: List<String>) = kotlinx.coroutines.flow.emptyFlow<com.netrik.core.network.ping.PingEvent>()
        }, StandardTestDispatcher(testScheduler))
        val events = PortScanner(FakeProber(emptySet(), emptySet()), hostProber, { _, _, _ -> null })
            .scan(listOf("10.0.0.7", "10.0.0.8"), listOf(53, 161), Protocol.Udp, 500, discoverFirst = false).toList()
        val ports = events.filterIsInstance<PortScanEvent.Port>()
        assertEquals(4, ports.size)
        assertTrue(ports.all { it.state == PortState.OpenFiltered })
        assertEquals(2, events.filterIsInstance<PortScanEvent.Progress>().last().hostsDone)
    }
}
