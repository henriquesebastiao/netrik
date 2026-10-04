package com.netrik.core.wifi

import com.netrik.core.network.WifiBand
import com.netrik.core.network.WifiChannels

/**
 * Horizontal axis of the spectrum, in 20 MHz "slots".
 * - 2.4 GHz: continuous frequency scale (channels overlap every 5 MHz).
 * - 5 GHz: 20 MHz channels in UNII blocks, with the gaps between blocks compressed.
 * - 6 GHz: contiguous 20 MHz channels (1, 5, 9 … 233).
 */
class SpectrumAxis private constructor(
    val band: WifiBand,
    /** Channels labeled on the axis and their position in slots. */
    val ticks: List<Pair<Int, Float>>,
    /** Total width in slots. */
    val slots: Float,
    /** Positions (in slots) where separators between blocks are drawn. */
    val separators: List<Float>,
    private val segments: List<Segment>,
) {
    private data class Segment(val startMhz: Int, val endMhz: Int, val startSlot: Float)

    /** Position in slots of a frequency (MHz), or null if outside the axis. */
    fun position(frequencyMhz: Double): Float? {
        val segment = segments.firstOrNull { frequencyMhz >= it.startMhz && frequencyMhz <= it.endMhz } ?: return null
        return segment.startSlot + ((frequencyMhz - segment.startMhz) / 20.0).toFloat()
    }

    /** Occupied range [start, end] in slots, clipped to the channel's block. */
    fun span(centerMhz: Int, widthMhz: Int): Pair<Float, Float>? {
        val segment = segmentOf(centerMhz) ?: return null
        val from = (centerMhz - widthMhz / 2.0).coerceAtLeast(segment.startMhz.toDouble())
        val to = (centerMhz + widthMhz / 2.0).coerceAtMost(segment.endMhz.toDouble())
        return (position(from) ?: return null) to (position(to) ?: return null)
    }

    private fun segmentOf(frequencyMhz: Int) = segments.firstOrNull { frequencyMhz >= it.startMhz && frequencyMhz <= it.endMhz }

    companion object {
        fun forBand(band: WifiBand): SpectrumAxis = when (band) {
            WifiBand.GHz2_4 -> {
                // 2397–2497 MHz: margin for channel 1 (2412) and 14 (2484) at 20 MHz.
                val segment = Segment(2397, 2497, 0f)
                val ticks = (1..13).map { ch -> ch to (WifiChannels.channelToFrequency(ch, band)!! - 2397) / 20f }
                SpectrumAxis(band, ticks, 5f, emptyList(), listOf(segment))
            }
            WifiBand.GHz5 -> {
                val blocks = listOf(36..64, 100..144, 149..177)
                var slot = 0f
                val segments = mutableListOf<Segment>()
                val ticks = mutableListOf<Pair<Int, Float>>()
                val separators = mutableListOf<Float>()
                blocks.forEachIndexed { i, block ->
                    if (i > 0) separators += slot
                    val first = 5000 + 5 * block.first
                    val last = 5000 + 5 * block.last
                    segments += Segment(first - 10, last + 10, slot)
                    block.step(4).forEach { ch -> ticks += ch to slot + (5000 + 5 * ch - (first - 10)) / 20f }
                    slot += (last - first) / 20f + 1
                }
                SpectrumAxis(band, ticks, slot, separators, segments)
            }
            WifiBand.GHz6 -> {
                val first = 5955
                val last = 7115
                val ticks = (1..233 step 4).map { ch -> ch to (5950 + 5 * ch - (first - 10)) / 20f }
                SpectrumAxis(band, ticks, (last - first) / 20f + 1, emptyList(), listOf(Segment(first - 10, last + 10, 0f)))
            }
        }
    }
}
