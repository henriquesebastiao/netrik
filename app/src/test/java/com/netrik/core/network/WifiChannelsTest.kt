package com.netrik.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiChannelsTest {

    @Test
    fun `frequências de 2,4 GHz`() {
        assertEquals(1, WifiChannels.frequencyToChannel(2412))
        assertEquals(6, WifiChannels.frequencyToChannel(2437))
        assertEquals(13, WifiChannels.frequencyToChannel(2472))
        assertEquals(14, WifiChannels.frequencyToChannel(2484))
        assertEquals(WifiBand.GHz2_4, WifiChannels.bandOf(2437))
    }

    @Test
    fun `frequências de 5 GHz`() {
        assertEquals(36, WifiChannels.frequencyToChannel(5180))
        assertEquals(100, WifiChannels.frequencyToChannel(5500))
        assertEquals(165, WifiChannels.frequencyToChannel(5825))
        assertEquals(WifiBand.GHz5, WifiChannels.bandOf(5180))
    }

    @Test
    fun `frequências de 6 GHz`() {
        assertEquals(1, WifiChannels.frequencyToChannel(5955))
        assertEquals(2, WifiChannels.frequencyToChannel(5935))
        assertEquals(37, WifiChannels.frequencyToChannel(6135))
        assertEquals(233, WifiChannels.frequencyToChannel(7115))
        assertEquals(WifiBand.GHz6, WifiChannels.bandOf(6135))
    }

    @Test
    fun `frequência fora das bandas ou fora da grade devolve null`() {
        assertNull(WifiChannels.frequencyToChannel(900))
        assertNull(WifiChannels.frequencyToChannel(2413))
        assertNull(WifiChannels.bandOf(60_000))
    }

    @Test
    fun `canal vira frequência`() {
        assertEquals(2412, WifiChannels.channelToFrequency(1, WifiBand.GHz2_4))
        assertEquals(2484, WifiChannels.channelToFrequency(14, WifiBand.GHz2_4))
        assertEquals(5180, WifiChannels.channelToFrequency(36, WifiBand.GHz5))
        assertEquals(5935, WifiChannels.channelToFrequency(2, WifiBand.GHz6))
        assertEquals(5955, WifiChannels.channelToFrequency(1, WifiBand.GHz6))
        assertNull(WifiChannels.channelToFrequency(15, WifiBand.GHz2_4))
        assertNull(WifiChannels.channelToFrequency(200, WifiBand.GHz5))
    }

    @Test
    fun `ida e volta preserva o canal`() {
        val cases = listOf(WifiBand.GHz2_4 to (1..14), WifiBand.GHz5 to listOf(36, 40, 44, 48, 52, 100, 149, 165))
        for ((band, channels) in cases) {
            for (channel in channels) {
                val freq = WifiChannels.channelToFrequency(channel, band)!!
                assertEquals(channel, WifiChannels.frequencyToChannel(freq))
            }
        }
    }
}
