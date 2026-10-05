package com.netrik.core.subnet

import java.math.BigInteger
import java.util.Locale

enum class IpVersion(val bits: Int) { V4(32), V6(128) }

/** IPv4 or IPv6 address as an unsigned integer ([BigInteger], so IPv6's 128 bits fit). */
data class IpAddress(val version: IpVersion, val value: BigInteger) : Comparable<IpAddress> {

    init {
        require(value.signum() >= 0 && value.bitLength() <= version.bits) { "Value out of range for $version" }
    }

    override fun compareTo(other: IpAddress): Int =
        compareValuesBy(this, other, { it.version }, { it.value })

    /** Dotted IPv4 or RFC 5952 compressed IPv6 ("2001:db8::1"). */
    fun format(): String = when (version) {
        IpVersion.V4 -> formatV4(value.toLong())
        IpVersion.V6 -> formatV6(value)
    }

    /** IPv6 with all 8 groups of 4 digits; IPv4 as is. */
    fun expanded(): String = when (version) {
        IpVersion.V4 -> format()
        IpVersion.V6 -> groups(value).joinToString(":") { String.format(Locale.ROOT, "%04x", it) }
    }

    override fun toString(): String = format()

    companion object {
        fun v4(value: Long) = IpAddress(IpVersion.V4, BigInteger.valueOf(value))

        /** IPv4 or IPv6 literal (IPv6 may come in brackets or with a zone, "fe80::1%wlan0"). Never does DNS. */
        fun parse(text: String): IpAddress? {
            val trimmed = text.trim()
            return if (':' in trimmed) parseV6(trimmed)?.let { IpAddress(IpVersion.V6, it) } else parseV4(trimmed)?.let(::v4)
        }

        fun parseV4(text: String): Long? {
            val parts = text.trim().split('.')
            if (parts.size != 4) return null
            var value = 0L
            for (part in parts) {
                if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return null
                val octet = part.toInt()
                if (octet > 255) return null
                value = (value shl 8) or octet.toLong()
            }
            return value
        }

        fun parseV6(text: String): BigInteger? {
            var s = text.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
            if (s.count { it == ':' } < 2) return null
            // Embedded IPv4 in the last 32 bits ("::ffff:192.0.2.1").
            val lastColon = s.lastIndexOf(':')
            val tail = s.substring(lastColon + 1)
            if ('.' in tail) {
                val v4 = parseV4(tail) ?: return null
                s = s.substring(0, lastColon + 1) + String.format(Locale.ROOT, "%x:%x", v4 shr 16, v4 and 0xFFFF)
            }
            val halves = s.split("::")
            if (halves.size > 2) return null
            val head = hexGroups(halves[0]) ?: return null
            val all = if (halves.size == 2) {
                val rest = hexGroups(halves[1]) ?: return null
                val missing = 8 - head.size - rest.size
                if (missing < 1) return null
                head + List(missing) { 0 } + rest
            } else {
                head
            }
            if (all.size != 8) return null
            return all.fold(BigInteger.ZERO) { acc, group -> acc.shiftLeft(16).or(BigInteger.valueOf(group.toLong())) }
        }

        private fun hexGroups(part: String): List<Int>? {
            if (part.isEmpty()) return emptyList()
            return part.split(':').map { group ->
                if (group.length !in 1..4 || group.any { Character.digit(it, 16) < 0 }) return null
                group.toInt(16)
            }
        }

        fun formatV4(value: Long): String = (3 downTo 0).joinToString(".") { ((value shr (it * 8)) and 0xFF).toString() }

        private fun groups(value: BigInteger): List<Int> =
            (7 downTo 0).map { value.shiftRight(it * 16).toInt() and 0xFFFF }

        /** RFC 5952: lowercase, no leading zeros, the longest run of 2+ zero groups (the first on a tie) as "::". */
        private fun formatV6(value: BigInteger): String {
            val groups = groups(value)
            // IPv4-mapped addresses keep the dotted tail (RFC 5952 section 5).
            if (groups.subList(0, 5).all { it == 0 } && groups[5] == 0xFFFF) {
                return "::ffff:" + formatV4((groups[6].toLong() shl 16) or groups[7].toLong())
            }
            var bestStart = -1
            var bestLength = 1
            var i = 0
            while (i < 8) {
                if (groups[i] == 0) {
                    var j = i
                    while (j < 8 && groups[j] == 0) j++
                    if (j - i > bestLength) {
                        bestStart = i
                        bestLength = j - i
                    }
                    i = j
                } else {
                    i++
                }
            }
            fun hex(range: List<Int>) = range.joinToString(":") { Integer.toHexString(it) }
            if (bestStart < 0) return hex(groups)
            return hex(groups.subList(0, bestStart)) + "::" + hex(groups.subList(bestStart + bestLength, 8))
        }
    }
}
