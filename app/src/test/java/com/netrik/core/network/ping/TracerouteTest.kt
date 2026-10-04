package com.netrik.core.network.ping

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TracerouteTest {

    private val host = ResolvedHost("google.com", "142.250.79.46", ipv6 = false)

    /** Fakes ping: answers by TTL (probes) or by IP (hop RTT measurement). */
    private class FakeRunner(
        private val byTtl: Map<Int, List<String>>,
        private val byAddress: Map<String, List<String>> = emptyMap(),
    ) : PingRunner {
        override fun run(command: List<String>): Flow<PingEvent> {
            val ttl = command.indexOf("-t").takeIf { it >= 0 }?.let { command[it + 1].toInt() }
            val lines = if (ttl != null) byTtl[ttl].orEmpty() else byAddress[command.last()].orEmpty()
            return flowOf(*(lines.mapNotNull(PingOutputParser::parse) + PingEvent.Exited(0)).toTypedArray())
        }
    }

    private class FakeResolver(private val names: Map<String, String>) : HostResolver {
        override suspend fun resolve(target: String) = null
        override suspend fun reverse(address: String) = names[address]
    }

    @Test
    fun `discovers the hops up to the destination`() = runTest {
        val runner = FakeRunner(
            byTtl = mapOf(
                1 to listOf("From 192.168.0.1 icmp_seq=1 Time to live exceeded"),
                2 to listOf("no answer yet for icmp_seq=1"),
                3 to listOf("From 187.16.212.9 icmp_seq=1 Time to live exceeded"),
                4 to listOf("64 bytes from 142.250.79.46: icmp_seq=1 ttl=117 time=12.4 ms"),
            ),
            byAddress = mapOf(
                "192.168.0.1" to listOf("64 bytes from 192.168.0.1: icmp_seq=1 ttl=64 time=1.2 ms"),
                "187.16.212.9" to emptyList(), // router that doesn't answer a direct ping
            ),
        )
        val resolver = FakeResolver(mapOf("192.168.0.1" to "router.lan", "142.250.79.46" to "gru14s23-in-f14.1e100.net"))

        val events = Traceroute(runner, resolver).run(host, TracerouteOptions(maxHops = 30, hopTimeoutSeconds = 3)).toList()

        val hops = events.filterIsInstance<TraceEvent.Hop>()
        assertEquals(listOf("192.168.0.1", null, "187.16.212.9", "142.250.79.46"), hops.map { it.address })
        assertTrue(hops.last().reachedDestination)
        assertEquals(12.4, hops.last().rttMs!!, 1e-9)

        val rtts = events.filterIsInstance<TraceEvent.HopRtt>().associate { it.hop to it.rttMs }
        assertEquals(1.2, rtts.getValue(1)!!, 1e-9)
        assertEquals(null, rtts.getValue(3))

        val names = events.filterIsInstance<TraceEvent.HopName>().associate { it.hop to it.hostname }
        assertEquals(mapOf(1 to "router.lan", 4 to "gru14s23-in-f14.1e100.net"), names)

        assertEquals(TraceEvent.Finished(reachedDestination = true, hops = 4), events.filterIsInstance<TraceEvent.Finished>().single())
    }

    @Test
    fun `stops at max hops without reaching the destination`() = runTest {
        val runner = FakeRunner(byTtl = emptyMap())
        val events = Traceroute(runner, FakeResolver(emptyMap())).run(host, TracerouteOptions(3, 1)).toList()
        assertEquals(3, events.filterIsInstance<TraceEvent.Hop>().count { it.address == null })
        assertEquals(TraceEvent.Finished(reachedDestination = false, hops = 3), events.last())
    }

    @Test
    fun `unreachable destination ends the route`() = runTest {
        val runner = FakeRunner(
            byTtl = mapOf(
                1 to listOf("From 192.168.0.1 icmp_seq=1 Time to live exceeded"),
                2 to listOf("From 10.0.0.1 icmp_seq=1 Destination Net Unreachable"),
            ),
        )
        val events = Traceroute(runner, FakeResolver(emptyMap())).run(host, TracerouteOptions(30, 1)).toList()
        assertEquals(TraceEvent.Finished(reachedDestination = false, hops = 2), events.filterIsInstance<TraceEvent.Finished>().single())
    }

    @Test
    fun `ping failure stops with an error`() = runTest {
        val runner = FakeRunner(byTtl = mapOf(1 to listOf("connect: Network is unreachable")))
        val events = Traceroute(runner, FakeResolver(emptyMap())).run(host, TracerouteOptions(30, 1)).toList()
        assertEquals(TraceEvent.Failed(1, "Network is unreachable"), events.last())
    }
}
