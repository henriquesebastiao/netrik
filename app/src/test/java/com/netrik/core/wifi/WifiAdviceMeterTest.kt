package com.netrik.core.wifi

import com.netrik.core.network.WifiBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiAdviceMeterTest {

    private fun net(bssid: String, channel: Int, dbm: Int, band: WifiBand = WifiBand.GHz2_4, width: Int = 20, center: Int? = null, connected: Boolean = false): WifiNetwork {
        val freq = if (band == WifiBand.GHz2_4) 2407 + 5 * channel else 5000 + 5 * channel
        return WifiNetwork("n$bssid", bssid, dbm, freq, band, channel, width, center ?: freq, WifiSecurity.Wpa2, connected)
    }

    @Test
    fun `2,4 GHz picks the least congested of 1, 6 and 11`() {
        val networks = listOf(
            net("A", 1, -50), net("B", 1, -55), // two strong on 1
            net("C", 6, -85), // one very weak on 6
            net("D", 11, -65), // one medium on 11
        )
        val advice = ChannelAdvisor.advise(networks, WifiBand.GHz2_4)
        assertEquals(listOf(6, 11, 1), advice.ranking.map { it.channel })
        assertEquals(1, advice.best!!.networks)
        assertNull(advice.current)
    }

    @Test
    fun `channels in between overlap their neighbors`() {
        // Channel 3 (2422 MHz, 2412–2432) overlaps channel 1 by 10 MHz and channel 6 by 5 MHz.
        val advice = ChannelAdvisor.advise(listOf(net("A", 3, -50)), WifiBand.GHz2_4)
        val scores = advice.ranking.associate { it.channel to it.score }
        assertEquals(0.5, scores.getValue(1), 1e-9)
        assertEquals(0.25, scores.getValue(6), 1e-9)
        assertEquals(0.0, scores.getValue(11), 1e-9)
        assertEquals(11, advice.best!!.channel)
    }

    @Test
    fun `the connected network is compared but not counted against itself`() {
        val networks = listOf(net("ME", 6, -40, connected = true), net("A", 6, -60), net("B", 1, -50), net("C", 11, -50))
        val advice = ChannelAdvisor.advise(networks, WifiBand.GHz2_4)
        assertEquals(6, advice.current!!.channel)
        assertEquals(1, advice.current!!.networks)
        assertTrue(advice.currentIsGood)
        // A strong neighbor moves onto 6: now 1 or 11 is clearly better.
        val worse = ChannelAdvisor.advise(networks + net("D", 6, -45), WifiBand.GHz2_4)
        assertFalse(worse.currentIsGood)
    }

    @Test
    fun `5 GHz counts wide channels and prefers non DFS on a tie`() {
        // One 80 MHz network centered at 5210 (36–48) covers 36, 40, 44 and 48.
        val wide = net("A", 36, -55, WifiBand.GHz5, width = 80, center = 5210)
        val advice = ChannelAdvisor.advise(listOf(wide), WifiBand.GHz5)
        val busy = advice.ranking.filter { it.score > 0 }.map { it.channel }.sorted()
        assertEquals(listOf(36, 40, 44, 48), busy)
        // Empty channels tie at 0: the first is a non-DFS one.
        assertFalse(advice.best!!.dfs)
        assertEquals(149, advice.best!!.channel)
        assertTrue(advice.ranking.first { it.channel == 100 }.dfs)
    }

    @Test
    fun `6 GHz uses the preferred scanning channels`() {
        assertEquals(15, ChannelAdvisor.CANDIDATES_6.size)
        assertEquals(listOf(5, 21, 37), ChannelAdvisor.CANDIDATES_6.take(3))
        assertEquals(229, ChannelAdvisor.CANDIDATES_6.last())
    }

    @Test
    fun `signal history keeps a window and computes statistics`() {
        var history = SignalHistory(windowMillis = 10_000)
        listOf(0L to -60, 4_000L to -50, 8_000L to -70, 12_000L to -66).forEach { (t, dbm) ->
            history = history.add(WifiLinkSample(t, dbm, null, null, null, null))
        }
        // The reading at 0 s fell out of the 10 s window.
        assertEquals(3, history.samples.size)
        assertEquals(-70, history.min)
        assertEquals(-50, history.max)
        assertEquals(-62, history.average)
        assertEquals(-66, history.latest!!.rssiDbm)
        assertNull(SignalHistory().average)
    }

    @Test
    fun `meter scale and beep pace`() {
        assertEquals(0f, SignalMeter.fraction(-100))
        assertEquals(1f, SignalMeter.fraction(-20))
        assertEquals(1_500L, SignalMeter.beepIntervalMillis(-95))
        assertEquals(150L, SignalMeter.beepIntervalMillis(-30))
        assertTrue(SignalMeter.beepIntervalMillis(-50) < SignalMeter.beepIntervalMillis(-80))
    }

    @Test
    fun `Wi-Fi standard from Android constants`() {
        assertEquals(WifiStandard.Wifi6, WifiStandard.fromAndroid(6))
        assertEquals(WifiStandard.Wifi7, WifiStandard.fromAndroid(8))
        assertEquals(WifiStandard.Wifi5, WifiStandard.fromAndroid(5))
        assertNull(WifiStandard.fromAndroid(0))
    }
}
