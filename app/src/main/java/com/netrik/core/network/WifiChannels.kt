package com.netrik.core.network

enum class WifiBand(val label: String) {
    GHz2_4("2.4"),
    GHz5("5"),
    GHz6("6"),
}

/** Conversão canal ↔ frequência central (MHz) segundo IEEE 802.11. */
object WifiChannels {

    fun bandOf(frequencyMhz: Int): WifiBand? = when (frequencyMhz) {
        in 2401..2495 -> WifiBand.GHz2_4
        in 5150..5895 -> WifiBand.GHz5
        in 5925..7125 -> WifiBand.GHz6
        else -> null
    }

    fun frequencyToChannel(frequencyMhz: Int): Int? = when (bandOf(frequencyMhz)) {
        WifiBand.GHz2_4 -> when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 && (frequencyMhz - 2407) % 5 == 0 -> (frequencyMhz - 2407) / 5
            else -> null
        }
        WifiBand.GHz5 -> if ((frequencyMhz - 5000) % 5 == 0) (frequencyMhz - 5000) / 5 else null
        WifiBand.GHz6 -> when {
            frequencyMhz == 5935 -> 2
            frequencyMhz >= 5955 && (frequencyMhz - 5950) % 5 == 0 -> (frequencyMhz - 5950) / 5
            else -> null
        }
        null -> null
    }

    fun channelToFrequency(channel: Int, band: WifiBand): Int? = when (band) {
        WifiBand.GHz2_4 -> when (channel) {
            in 1..13 -> 2407 + 5 * channel
            14 -> 2484
            else -> null
        }
        WifiBand.GHz5 -> if (channel in 32..177) 5000 + 5 * channel else null
        WifiBand.GHz6 -> when (channel) {
            2 -> 5935
            in 1..233 -> 5950 + 5 * channel
            else -> null
        }
    }
}
