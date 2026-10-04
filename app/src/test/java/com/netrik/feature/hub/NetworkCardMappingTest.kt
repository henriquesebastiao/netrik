package com.netrik.feature.hub

import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.CurrentNetwork.Transport
import com.netrik.core.network.Ipv4Address
import com.netrik.core.network.WifiBand
import com.netrik.core.network.WifiDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkCardMappingTest {

    private val wifi = CurrentNetwork.Connected(
        transport = Transport.Wifi,
        validated = true,
        ipv4 = Ipv4Address("192.168.0.42", 24),
        gateway = "192.168.0.1",
        dnsServers = listOf("1.1.1.1", "8.8.8.8"),
        ipv6 = "2804:14d:5c83:8a10::1f3a",
        wifi = WifiDetails(ssid = "Escritório-5G", rssiDbm = -54, frequencyMhz = 5180),
        carrierName = null,
    )

    @Test
    fun `Wi-Fi conectado preenche todos os campos`() {
        val card = wifi.toCardState() as NetworkCardState.Connected
        assertEquals("Escritório-5G", card.name)
        assertTrue(card.hasInternet)
        assertEquals("192.168.0.42", card.localIp)
        assertEquals("255.255.255.0 /24", card.maskCidr)
        assertEquals("192.168.0.1", card.gateway)
        assertEquals("1.1.1.1, 8.8.8.8", card.dns)
        assertEquals("2804:14d:5c83:8a10::1f3a", card.ipv6)
        assertEquals(WifiSignal(rssiDbm = -54, band = WifiBand.GHz5, channel = 36), card.signal)
        assertFalse(card.ssidHidden)
    }

    @Test
    fun `SSID oculto é sinalizado sem inventar nome`() {
        val card = wifi.copy(wifi = WifiDetails(ssid = null, rssiDbm = -70, frequencyMhz = 2437))
            .toCardState() as NetworkCardState.Connected
        assertNull(card.name)
        assertTrue(card.ssidHidden)
        assertEquals(6, card.signal?.channel)
    }

    @Test
    fun `dados ausentes ficam nulos`() {
        val card = wifi.copy(ipv4 = null, gateway = null, dnsServers = emptyList(), ipv6 = null, validated = false)
            .toCardState() as NetworkCardState.Connected
        assertNull(card.localIp)
        assertNull(card.maskCidr)
        assertNull(card.gateway)
        assertNull(card.dns)
        assertNull(card.ipv6)
        assertFalse(card.hasInternet)
    }

    @Test
    fun `dados móveis usam o nome da operadora e não têm sinal Wi-Fi`() {
        val card = wifi.copy(transport = Transport.Cellular, wifi = null, carrierName = "Operadora X")
            .toCardState() as NetworkCardState.Connected
        assertEquals("Operadora X", card.name)
        assertNull(card.signal)
        assertFalse(card.ssidHidden)
    }

    @Test
    fun `largura do canal vem do scan pelo BSSID`() {
        val withBssid = wifi.copy(wifi = WifiDetails("Escritório-5G", -54, 5180, bssid = "C0:06:C3:4A:12:80"))
        val card = withBssid.toCardState(mapOf("C0:06:C3:4A:12:80" to 80)) as NetworkCardState.Connected
        assertEquals(80, card.signal?.widthMhz)
        val noScan = withBssid.toCardState() as NetworkCardState.Connected
        assertNull(noScan.signal?.widthMhz)
    }

    @Test
    fun `sem conexão`() {
        assertEquals(NetworkCardState.Disconnected, CurrentNetwork.Disconnected.toCardState())
    }

    @Test
    fun `chave da rede muda quando o endereço muda`() {
        val a = (wifi.toCardState() as NetworkCardState.Connected).networkKey
        val b = (wifi.copy(ipv4 = Ipv4Address("10.0.0.5", 8)).toCardState() as NetworkCardState.Connected).networkKey
        val c = (wifi.copy(wifi = wifi.wifi?.copy(rssiDbm = -80)).toCardState() as NetworkCardState.Connected).networkKey
        assertNotEquals(a, b)
        assertEquals("o sinal oscilar não troca de rede", a, c)
    }
}
