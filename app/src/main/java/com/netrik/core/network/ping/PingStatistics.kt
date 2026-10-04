package com.netrik.core.network.ping

import kotlin.math.abs
import kotlin.math.roundToInt

/** A ping packet: [timeMs] null = no reply (timeout). */
data class PingSample(val seq: Int, val ttl: Int?, val timeMs: Double?)

data class PingStats(
    val sent: Int,
    val received: Int,
    /** Loss as a rounded whole percentage. */
    val lossPercent: Int,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
    /** Mean absolute difference between consecutive replies (RFC 3550, without smoothing). */
    val jitterMs: Double?,
) {
    companion object {
        fun of(samples: List<PingSample>): PingStats {
            val times = samples.sortedBy { it.seq }.mapNotNull { it.timeMs }
            val sent = samples.size
            val received = times.size
            val jitter = if (times.size > 1) times.zipWithNext { a, b -> abs(b - a) }.average() else null
            return PingStats(
                sent = sent,
                received = received,
                lossPercent = if (sent == 0) 0 else ((sent - received) * 100.0 / sent).roundToInt(),
                minMs = times.minOrNull(),
                avgMs = times.takeIf { it.isNotEmpty() }?.average(),
                maxMs = times.maxOrNull(),
                jitterMs = jitter,
            )
        }
    }
}
