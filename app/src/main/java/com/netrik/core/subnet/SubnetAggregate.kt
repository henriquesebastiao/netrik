package com.netrik.core.subnet

import java.math.BigInteger

/** Aggregation of one IP version: the exact summary and the single block that contains everything. */
data class AggregateSummary(
    /** Fewest CIDR blocks that cover exactly the given networks (overlaps and neighbors merged). */
    val cidrs: List<Cidr>,
    /** Smallest single block containing all of them (a summary route). */
    val supernet: Cidr,
    /** Addresses covered by the input. */
    val covered: BigInteger,
) {
    /** Addresses the [supernet] includes beyond the input: 0 means it summarizes exactly. */
    val extra: BigInteger get() = supernet.size - covered
}

/** A typed CIDR with host bits, kept as its network ("10.0.0.5/24" → 10.0.0.0/24). */
data class NormalizedEntry(val line: Int, val typed: String, val cidr: Cidr)

data class AggregateResult(
    val v4: AggregateSummary?,
    val v6: AggregateSummary?,
    /** 1-based lines that aren't a network, address or range. */
    val invalidLines: List<Int>,
    val normalized: List<NormalizedEntry>,
) {
    val isEmpty: Boolean get() = v4 == null && v6 == null
}

object SubnetAggregator {

    const val MAX_LINES = 4_096

    /**
     * One entry per line: a network ("10.0.0.0/25"), an address ("10.0.1.7") or a range ("10.0.1.5-10.0.1.20").
     * Empty lines and lines starting with # are ignored. IPv4 and IPv6 are summarized separately.
     */
    fun aggregate(text: String): AggregateResult {
        val intervals = mutableMapOf<IpVersion, MutableList<Pair<BigInteger, BigInteger>>>()
        val invalid = mutableListOf<Int>()
        val normalized = mutableListOf<NormalizedEntry>()
        text.lines().take(MAX_LINES).forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
            val interval = parseLine(line, index + 1, normalized)
            if (interval == null) {
                invalid += index + 1
            } else {
                intervals.getOrPut(interval.first) { mutableListOf() } += interval.second to interval.third
            }
        }
        return AggregateResult(
            v4 = intervals[IpVersion.V4]?.let { summarize(IpVersion.V4, it) },
            v6 = intervals[IpVersion.V6]?.let { summarize(IpVersion.V6, it) },
            invalidLines = invalid,
            normalized = normalized,
        )
    }

    private fun parseLine(line: String, number: Int, normalized: MutableList<NormalizedEntry>): Triple<IpVersion, BigInteger, BigInteger>? {
        if ('-' in line) {
            val (startText, endText) = line.split('-', limit = 2).map { it.trim() }
            val start = IpAddress.parse(startText) ?: return null
            val end = IpAddress.parse(endText) ?: return null
            if (start.version != end.version || start.value > end.value) return null
            return Triple(start.version, start.value, end.value)
        }
        val parsed = SubnetParser.parse(line) as? ParsedSubnet.Ok ?: return null
        val cidr = parsed.cidr
        if (cidr.network != parsed.address) normalized += NormalizedEntry(number, line, cidr)
        return Triple(cidr.version, cidr.first.value, cidr.last.value)
    }

    private fun summarize(version: IpVersion, intervals: List<Pair<BigInteger, BigInteger>>): AggregateSummary {
        val merged = mutableListOf<Pair<BigInteger, BigInteger>>()
        intervals.sortedBy { it.first }.forEach { (start, end) ->
            val last = merged.lastOrNull()
            if (last != null && start <= last.second + BigInteger.ONE) {
                merged[merged.lastIndex] = last.first to last.second.max(end)
            } else {
                merged += start to end
            }
        }
        val cidrs = merged.flatMap { (start, end) -> rangeToCidrs(version, start, end) }
        val covered = merged.fold(BigInteger.ZERO) { acc, (start, end) -> acc + (end - start + BigInteger.ONE) }
        return AggregateSummary(cidrs, supernet(version, merged.first().first, merged.last().second), covered)
    }

    /** Fewest aligned blocks covering exactly [start]..[end]. */
    fun rangeToCidrs(version: IpVersion, start: BigInteger, end: BigInteger): List<Cidr> {
        val bits = version.bits
        val result = mutableListOf<Cidr>()
        var current = start
        while (current <= end) {
            // Largest block aligned at current, then shrunk until it ends inside the range.
            var hostBits = if (current.signum() == 0) bits else current.lowestSetBit
            while (current + BigInteger.ONE.shiftLeft(hostBits) - BigInteger.ONE > end) hostBits--
            result += Cidr(IpAddress(version, current), bits - hostBits)
            current += BigInteger.ONE.shiftLeft(hostBits)
        }
        return result
    }

    /** Smallest block containing [start] and [end]: their common leading bits. */
    fun supernet(version: IpVersion, start: BigInteger, end: BigInteger): Cidr {
        val prefix = version.bits - start.xor(end).bitLength()
        return Cidr.containing(IpAddress(version, start), prefix)
    }
}
