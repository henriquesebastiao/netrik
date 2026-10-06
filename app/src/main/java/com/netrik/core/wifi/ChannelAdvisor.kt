package com.netrik.core.wifi

import com.netrik.core.network.WifiBand
import com.netrik.core.network.WifiChannels

/** Congestion of one 20 MHz channel: [score] sums the overlapping networks, weighted by how strong they are here. */
data class ChannelScore(
    val channel: Int,
    val centerMhz: Int,
    val score: Double,
    /** Networks that overlap this channel. */
    val networks: Int,
    /** 5 GHz channels that need radar detection (DFS): the router may have to leave them. */
    val dfs: Boolean,
)

data class ChannelAdvice(
    val band: WifiBand,
    /** Least congested first. */
    val ranking: List<ChannelScore>,
    /** The connected network's channel, when it's on this band. */
    val current: ChannelScore?,
) {
    val best: ChannelScore? get() = ranking.firstOrNull()

    /** The connected network is already on (or about as good as) the best channel. */
    val currentIsGood: Boolean
        get() {
            val now = current ?: return false
            val top = best ?: return false
            return now.score <= top.score + GOOD_ENOUGH_MARGIN
        }

    companion object {
        /** About one weak neighbor: smaller differences aren't worth changing the router's channel. */
        const val GOOD_ENOUGH_MARGIN = 0.35
    }
}

/**
 * Suggests the least congested channel from what this phone sees, where it is. Candidates: 1, 6 and 11 on 2.4 GHz
 * (the ones that don't overlap each other), the 20 MHz channels on 5 GHz (DFS ones flagged) and the preferred
 * scanning channels (PSC) on 6 GHz. Pure logic.
 */
object ChannelAdvisor {

    val CANDIDATES_2_4 = listOf(1, 6, 11)
    val CANDIDATES_5 = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
    /** 6 GHz preferred scanning channels: 5, 21, 37, ... 229. */
    val CANDIDATES_6 = (5..229 step 16).toList()

    private const val CANDIDATE_WIDTH = 20

    fun advise(networks: List<WifiNetwork>, band: WifiBand): ChannelAdvice {
        val inBand = networks.filter { it.band == band }
        val connected = inBand.firstOrNull { it.connected }
        // The connected network doesn't compete with itself.
        val others = inBand.filterNot { it.connected }
        val ranking = candidates(band).mapNotNull { channel ->
            WifiChannels.channelToFrequency(channel, band)?.let { score(channel, it, band, others) }
        }.sortedWith(compareBy<ChannelScore>({ it.score }, { it.dfs }, { it.channel }))
        val current = connected?.channel?.let { channel -> score(channel, connected.frequencyMhz, band, others) }
        return ChannelAdvice(band, ranking, current)
    }

    private fun candidates(band: WifiBand): List<Int> = when (band) {
        WifiBand.GHz2_4 -> CANDIDATES_2_4
        WifiBand.GHz5 -> CANDIDATES_5
        WifiBand.GHz6 -> CANDIDATES_6
    }

    private fun score(channel: Int, centerMhz: Int, band: WifiBand, networks: List<WifiNetwork>): ChannelScore {
        val low = centerMhz - CANDIDATE_WIDTH / 2
        val high = centerMhz + CANDIDATE_WIDTH / 2
        var score = 0.0
        var count = 0
        networks.forEach { n ->
            val nLow = n.centerMhz - n.widthMhz / 2
            val nHigh = n.centerMhz + n.widthMhz / 2
            val overlap = minOf(high, nHigh) - maxOf(low, nLow)
            if (overlap > 0) {
                count++
                score += overlap.toDouble() / CANDIDATE_WIDTH * weight(n.rssiDbm)
            }
        }
        return ChannelScore(channel, centerMhz, score, count, dfs = band == WifiBand.GHz5 && isDfs(channel))
    }

    /** A strong neighbor disturbs much more than one barely heard. */
    fun weight(rssiDbm: Int): Double = when (SignalQuality.of(rssiDbm)) {
        SignalQuality.Excellent -> 1.0
        SignalQuality.Good -> 0.6
        SignalQuality.Weak -> 0.3
        SignalQuality.VeryWeak -> 0.1
    }

    /** U-NII-2A and U-NII-2C (52–144) need DFS. */
    fun isDfs(channel: Int): Boolean = channel in 52..144
}
