package com.netrik.core.subnet

import java.math.BigInteger

/** How to split a network into equal parts. */
sealed interface SplitBy {
    data class Prefix(val prefix: Int) : SplitBy
    data class Count(val subnets: Long) : SplitBy
    data class Hosts(val hosts: Long) : SplitBy
}

enum class SplitError {
    /** The new prefix is shorter than the network's own. */
    PrefixTooShort,
    /** Longer than /32 (/128). */
    PrefixTooLong,
    /** Zero or negative number of subnets/hosts. */
    NotPositive,
    /** Asked for more subnets, or bigger subnets, than the network holds. */
    DoesNotFit,
}

sealed interface SplitResult {
    /** [subnets] holds the first [MAX_LISTED] blocks; [count] is the real total. */
    data class Ok(val parent: Cidr, val prefix: Int, val count: BigInteger, val subnets: List<Cidr>, val usablePerSubnet: BigInteger) : SplitResult
    data class Error(val error: SplitError) : SplitResult
}

/** One line of a VLSM plan: an optional name and how many hosts it needs. */
data class VlsmRequest(val label: String?, val hosts: Long)

/** [cidr] null = didn't fit in what was left of the network. */
data class VlsmAllocation(val request: VlsmRequest, val cidr: Cidr?)

data class VlsmResult(
    val parent: Cidr,
    /** Largest first, the order they were allocated in. */
    val allocations: List<VlsmAllocation>,
    val used: BigInteger,
    val free: BigInteger,
) {
    val allFit: Boolean get() = allocations.all { it.cidr != null }
}

object SubnetSplitter {

    const val MAX_LISTED = 256
    const val MAX_VLSM_REQUESTS = 256

    fun split(parent: Cidr, by: SplitBy): SplitResult {
        val bits = parent.version.bits
        val prefix = when (by) {
            is SplitBy.Prefix -> when {
                by.prefix < parent.prefix -> return SplitResult.Error(SplitError.PrefixTooShort)
                by.prefix > bits -> return SplitResult.Error(SplitError.PrefixTooLong)
                else -> by.prefix
            }
            is SplitBy.Count -> {
                if (by.subnets < 1) return SplitResult.Error(SplitError.NotPositive)
                parent.prefix + Cidr.bitsFor(BigInteger.valueOf(by.subnets))
            }
            is SplitBy.Hosts -> {
                if (by.hosts < 1) return SplitResult.Error(SplitError.NotPositive)
                prefixForHosts(parent.version, by.hosts).also {
                    if (it < parent.prefix) return SplitResult.Error(SplitError.DoesNotFit)
                }
            }
        }
        if (prefix > bits) return SplitResult.Error(SplitError.DoesNotFit)
        val count = BigInteger.ONE.shiftLeft(prefix - parent.prefix)
        val step = BigInteger.ONE.shiftLeft(bits - prefix)
        val listed = count.min(BigInteger.valueOf(MAX_LISTED.toLong())).toInt()
        val subnets = (0 until listed).map { i ->
            Cidr(IpAddress(parent.version, parent.network.value + step * BigInteger.valueOf(i.toLong())), prefix)
        }
        return SplitResult.Ok(parent, prefix, count, subnets, usableHosts(parent.version, prefix))
    }

    /**
     * Smallest block for [hosts] devices. IPv4 keeps the network and broadcast addresses out (2 hosts → /30, the
     * usual choice when planning; /31 point-to-point links are left to the user); IPv6 counts every address.
     */
    fun prefixForHosts(version: IpVersion, hosts: Long): Int = when (version) {
        IpVersion.V4 -> if (hosts == 1L) 32 else 32 - Cidr.bitsFor(BigInteger.valueOf(hosts) + TWO)
        IpVersion.V6 -> 128 - Cidr.bitsFor(BigInteger.valueOf(hosts))
    }.coerceAtLeast(0)

    /** Hosts a block can hold: IPv4 without network and broadcast (RFC 3021 for /31 and /32). */
    fun usableHosts(version: IpVersion, prefix: Int): BigInteger {
        val size = BigInteger.ONE.shiftLeft(version.bits - prefix)
        return when {
            version == IpVersion.V6 -> size
            prefix >= 31 -> size
            else -> size - TWO
        }
    }

    /**
     * VLSM: blocks for each request, largest first, packed from the start of [parent]. Allocating in decreasing
     * size keeps every block aligned without gaps; a request that no longer fits is reported and skipped.
     */
    fun vlsm(parent: Cidr, requests: List<VlsmRequest>): VlsmResult {
        val bits = parent.version.bits
        val ordered = requests.sortedByDescending { it.hosts }
        var cursor = parent.network.value
        val end = parent.last.value
        var used = BigInteger.ZERO
        val allocations = ordered.map { request ->
            val prefix = prefixForHosts(parent.version, request.hosts)
            val size = BigInteger.ONE.shiftLeft(bits - prefix)
            // Align (only needed after a request was skipped).
            val aligned = cursor.add(size - BigInteger.ONE).divide(size).multiply(size)
            if (prefix >= parent.prefix && aligned + size - BigInteger.ONE <= end) {
                cursor = aligned + size
                used += size
                VlsmAllocation(request, Cidr(IpAddress(parent.version, aligned), prefix))
            } else {
                VlsmAllocation(request, null)
            }
        }
        return VlsmResult(parent, allocations, used, parent.size - used)
    }

    /**
     * Reads a VLSM plan: one request per line (or separated by commas/semicolons), as "120", "Sales 120",
     * "Sales: 120" or "Sales = 120". The second value is the 1-based index of the first invalid entry, if any.
     */
    fun parseRequests(text: String): Pair<List<VlsmRequest>, Int?> {
        val entries = text.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        val requests = mutableListOf<VlsmRequest>()
        entries.forEachIndexed { index, entry ->
            val match = REQUEST.matchEntire(entry) ?: return requests to index + 1
            val hosts = match.groupValues[2].toLongOrNull()?.takeIf { it >= 1 } ?: return requests to index + 1
            requests += VlsmRequest(match.groupValues[1].trim().ifEmpty { null }, hosts)
            if (requests.size > MAX_VLSM_REQUESTS) return requests to index + 1
        }
        return requests to null
    }

    /** Optional label, a ":", "=" or spaces, then the number of hosts. */
    private val REQUEST = Regex("""^(?:(.*?)(?:\s*[:=]\s*|\s+))?(\d{1,12})$""")
}

/** BigInteger.TWO is Java 9+ (Android 13); minSdk is 26. */
internal val TWO: BigInteger = BigInteger.valueOf(2)
