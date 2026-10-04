package com.netrik.core.network.ping

import com.netrik.core.network.ping.PingOptionsValidator.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingLogicTest {

    @Test
    fun `estatísticas com perdas`() {
        val samples = listOf(
            PingSample(1, 117, 12.0),
            PingSample(2, 117, 14.0),
            PingSample(3, null, null),
            PingSample(4, 117, 11.0),
        )
        val stats = PingStats.of(samples)
        assertEquals(4, stats.sent)
        assertEquals(3, stats.received)
        assertEquals(25, stats.lossPercent)
        assertEquals(11.0, stats.minMs!!, 1e-9)
        assertEquals(12.333, stats.avgMs!!, 1e-3)
        assertEquals(14.0, stats.maxMs!!, 1e-9)
        // |14-12| e |11-14| entre respostas consecutivas
        assertEquals(2.5, stats.jitterMs!!, 1e-9)
    }

    @Test
    fun `estatísticas sem respostas`() {
        val stats = PingStats.of(listOf(PingSample(1, null, null), PingSample(2, null, null)))
        assertEquals(100, stats.lossPercent)
        assertNull(stats.minMs)
        assertNull(stats.avgMs)
        assertNull(stats.jitterMs)
        assertEquals(0, PingStats.of(emptyList()).lossPercent)
    }

    @Test
    fun `opções válidas do ping`() {
        val ok = PingOptionsValidator.ping("10", "0,5", "56", "2") as Result.Ok
        assertEquals(PingOptions(10, 0.5, 56, 2), ok.value)
        val continuous = PingOptionsValidator.ping(null, "1", "0", "1") as Result.Ok
        assertNull(continuous.value.count)
    }

    @Test
    fun `opções inválidas apontam os campos`() {
        val invalid = PingOptionsValidator.ping("0", "0.1", "70000", "") as Result.Invalid
        assertEquals(setOf(OptionField.Count, OptionField.Interval, OptionField.Size, OptionField.Timeout), invalid.errors)
        val trace = PingOptionsValidator.traceroute("65", "0") as Result.Invalid
        assertEquals(setOf(OptionField.MaxHops, OptionField.HopTimeout), trace.errors)
        assertEquals(TracerouteOptions(30, 3), (PingOptionsValidator.traceroute("30", "3") as Result.Ok).value)
    }

    @Test
    fun `linha de comando do ping`() {
        assertEquals(
            listOf("/system/bin/ping", "-n", "-O", "-c", "10", "-i", "0.5", "-s", "56", "-W", "2", "8.8.8.8"),
            PingCommand.build("8.8.8.8", ipv6 = false, PingOptions(10, 0.5, 56, 2)),
        )
        assertEquals(
            listOf("/system/bin/ping6", "-n", "-O", "-i", "1", "-s", "56", "-W", "2", "::1"),
            PingCommand.build("::1", ipv6 = true, PingOptions(null, 1.0, 56, 2)),
        )
        assertEquals(
            listOf("/system/bin/ping", "-n", "-c", "1", "-W", "3", "-t", "5", "8.8.8.8"),
            PingCommand.probe("8.8.8.8", ipv6 = false, ttl = 5, timeoutSeconds = 3),
        )
    }

    @Test
    fun `validação do alvo`() {
        listOf("8.8.8.8", "google.com", "srv-01.lan", "localhost", "2804:14d:5c83:8a10::1f3a", "::1").forEach {
            assertTrue(it, TargetValidator.isValid(it))
        }
        listOf("", "  ", "google com", "exa_mple..com", "http://google.com", "-abc.com", "a:b").forEach {
            assertFalse(it, TargetValidator.isValid(it))
        }
    }
}
