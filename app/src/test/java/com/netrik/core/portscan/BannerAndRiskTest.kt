package com.netrik.core.portscan

import com.netrik.core.lan.ReachabilityProber
import com.netrik.core.network.ping.PingEvent
import com.netrik.core.network.ping.PingRunner
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerAndRiskTest {

    private fun greeting(bytes: ByteArray, port: Int) = BannerParser.greeting(bytes, bytes.size, port)
    private fun greeting(text: String, port: Int) = greeting(text.toByteArray(Charsets.ISO_8859_1), port)

    @Test
    fun `greetings of services that speak first`() {
        assertEquals("SSH-2.0-OpenSSH_9.6p1 Ubuntu-3ubuntu13", greeting("SSH-2.0-OpenSSH_9.6p1 Ubuntu-3ubuntu13\r\n", 22))
        assertEquals("220 (vsFTPd 3.0.5)", greeting("220 (vsFTPd 3.0.5)\r\n", 21))
        // Multi-line greetings: the first line.
        assertEquals("220-mail.example.com ESMTP Postfix", greeting("220-mail.example.com ESMTP Postfix\r\n220 ready\r\n", 25))
        assertEquals("RFB 003.008", greeting("RFB 003.008\n", 5900))
        assertNull(greeting("\r\n\u0000\u0001", 1234))
    }

    @Test
    fun `control characters are removed and the length is limited`() {
        assertEquals("Hello world", BannerParser.clean("He\u0007llo\tworld\u001b"))
        assertEquals(BannerParser.MAX_LENGTH, BannerParser.clean("x".repeat(500))!!.length)
    }

    @Test
    fun `telnet negotiation is stripped`() {
        val iac = 0xFF.toByte()
        val bytes = byteArrayOf(iac, 251.toByte(), 1, iac, 253.toByte(), 31, iac, 250.toByte(), 24, 1, iac, 240.toByte()) +
            "\r\nUbuntu 24.04 LTS\r\nlogin: ".toByteArray()
        assertEquals("Ubuntu 24.04 LTS", greeting(bytes, 23))
    }

    @Test
    fun `MySQL and MariaDB handshakes`() {
        fun packet(payload: ByteArray) = byteArrayOf(payload.size.toByte(), 0, 0, 0) + payload
        val mysql = packet(byteArrayOf(10) + "8.0.36".toByteArray() + byteArrayOf(0, 1, 2, 3))
        assertEquals("MySQL 8.0.36", greeting(mysql, 3306))
        val maria = packet(byteArrayOf(10) + "5.5.5-10.11.6-MariaDB-0ubuntu0.24.04.1".toByteArray() + byteArrayOf(0, 9))
        assertEquals("MariaDB 10.11.6", greeting(maria, 3307))
        val error = packet(byteArrayOf(0xFF.toByte(), 0x6A, 0x04) + "Host '10.0.2.16' is not allowed to connect to this MySQL server".toByteArray())
        assertEquals("MySQL: Host '10.0.2.16' is not allowed to connect to this MySQL server", greeting(error, 3306))
    }

    @Test
    fun `HTTP status and server headers`() {
        val response = "HTTP/1.1 200 OK\r\nDate: x\r\nServer: nginx/1.24.0\r\nX-Powered-By: PHP/8.2\r\n\r\n"
        assertEquals("HTTP 200 · nginx/1.24.0 · PHP/8.2", BannerParser.http(response))
        assertEquals("HTTP 401", BannerParser.http("HTTP/1.0 401 Unauthorized\r\nWWW-Authenticate: Basic\r\n\r\n"))
        assertEquals("HTTP 301 · lighttpd", BannerParser.http("HTTP/2 301\nserver: lighttpd\n\n"))
        assertNull(BannerParser.http("SSH-2.0-OpenSSH"))
    }

    @Test
    fun `certificate common name`() {
        assertEquals("router.local", BannerParser.commonName("CN=router.local,O=Acme,C=BR"))
        assertEquals("Acme, Inc", BannerParser.commonName("O=x,CN=Acme\\, Inc"))
        assertEquals("O=Acme,C=BR", BannerParser.commonName("O=Acme,C=BR"))
    }

    @Test
    fun `risky ports by protocol`() {
        assertEquals(RiskReason.Telnet, PortRisks.of(Protocol.Tcp, 23))
        assertEquals(RiskLevel.High, PortRisks.of(Protocol.Tcp, 445)!!.level)
        assertEquals(RiskLevel.Medium, PortRisks.of(Protocol.Tcp, 8291)!!.level)
        assertEquals(RiskReason.Snmp, PortRisks.of(Protocol.Udp, 161))
        assertNull(PortRisks.of(Protocol.Tcp, 161))
        assertNull(PortRisks.of(Protocol.Tcp, 443))
        assertNull(PortRisks.of(Protocol.Udp, 23))
    }

    @Test
    fun `banners are grabbed only for open TCP ports when asked`() = runTest {
        val prober = object : PortProber {
            override suspend fun tcp(ip: String, port: Int, timeoutMs: Int) = if (port == 22 || port == 80) PortState.Open else PortState.Closed
            override suspend fun udp(ip: String, port: Int, timeoutMs: Int) = PortState.Open
        }
        val asked = mutableListOf<Int>()
        val grabber = BannerGrabber { _, port, _ ->
            asked += port
            if (port == 22) ServiceBanner("SSH-2.0-OpenSSH_9.6") else null
        }
        val hostProber = ReachabilityProber(object : PingRunner {
            override fun run(command: List<String>) = emptyFlow<PingEvent>()
        }, StandardTestDispatcher(testScheduler))
        val scanner = PortScanner(prober, hostProber, grabber)

        val events = scanner.scan(listOf("10.0.0.7"), listOf(22, 23, 80), Protocol.Tcp, 500, discoverFirst = false, grabBanners = true).toList()
        assertEquals(listOf(22, 80), asked.sorted())
        assertEquals(listOf(PortScanEvent.Banner("10.0.0.7", 22, ServiceBanner("SSH-2.0-OpenSSH_9.6"))), events.filterIsInstance<PortScanEvent.Banner>())

        asked.clear()
        scanner.scan(listOf("10.0.0.7"), listOf(22), Protocol.Tcp, 500, discoverFirst = false, grabBanners = false).toList()
        scanner.scan(listOf("10.0.0.7"), listOf(22), Protocol.Udp, 500, discoverFirst = false, grabBanners = true).toList()
        assertTrue(asked.isEmpty())
    }
}
