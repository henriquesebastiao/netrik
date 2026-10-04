package com.netrik.core.portscan

import com.netrik.core.network.Ipv4

enum class Protocol { Tcp, Udp }

/** State of a port, with Nmap semantics. */
enum class PortState {
    Open,
    Closed,
    /** TCP with no reply in time: something dropped the packet. */
    Filtered,
    /** UDP with no reply: may be open (the service didn't answer the probe) or filtered. */
    OpenFiltered,
}

/** Typed port list: "22,80,443,8000-8100". */
sealed interface PortList {
    data class Valid(val ports: List<Int>, val singles: Int, val ranges: Int) : PortList
    data object Empty : PortList
    /** Item that is neither a number nor a range. */
    data class Malformed(val token: String) : PortList
    /** Outside 1–65535 or a reversed range. */
    data class OutOfRange(val token: String) : PortList

    companion object {
        private val item = Regex("""^(\d{1,5})(?:\s*-\s*(\d{1,5}))?$""")

        fun parse(text: String): PortList {
            val tokens = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return Empty
            val ports = sortedSetOf<Int>()
            var singles = 0
            var ranges = 0
            for (token in tokens) {
                val m = item.matchEntire(token) ?: return Malformed(token)
                val from = m.groupValues[1].toInt()
                val to = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: from
                if (from < 1 || to > 65_535 || from > to) return OutOfRange(token)
                if (from == to) singles++ else ranges++
                for (p in from..to) ports += p
            }
            return Valid(ports.toList(), singles, ranges)
        }
    }
}

/** Network in CIDR notation typed in Network mode. */
object Cidr {
    /** Largest network accepted for port scanning: /22 (1,022 hosts). */
    const val MIN_PREFIX = 22

    sealed interface Result {
        data class Valid(val address: String, val prefixLength: Int) : Result
        data object Malformed : Result
        data object TooLarge : Result
    }

    fun parse(text: String): Result {
        val parts = text.trim().split('/')
        if (parts.size != 2) return Result.Malformed
        val ip = parts[0].trim()
        val prefix = parts[1].trim().toIntOrNull() ?: return Result.Malformed
        if (Ipv4.parse(ip) == null || prefix !in 0..32) return Result.Malformed
        if (prefix < MIN_PREFIX) return Result.TooLarge
        return Result.Valid(ip, prefix)
    }
}

/** Worst-case estimate: concurrent batches waiting for the whole timeout. */
object ScanEstimate {
    fun concurrency(protocol: Protocol): Int = when (protocol) {
        Protocol.Tcp -> PortScanner.TCP_CONCURRENCY
        Protocol.Udp -> PortScanner.UDP_CONCURRENCY
    }

    fun seconds(checks: Long, protocol: Protocol, timeoutMs: Int): Long {
        if (checks <= 0) return 0
        val batches = (checks + concurrency(protocol) - 1) / concurrency(protocol)
        return (batches * timeoutMs + 999) / 1000
    }
}
