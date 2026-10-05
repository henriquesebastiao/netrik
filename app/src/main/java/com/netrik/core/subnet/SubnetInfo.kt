package com.netrik.core.subnet

import java.math.BigInteger
import java.util.Locale

/** Special-purpose ranges (IANA registries); the UI turns each into a translated label. */
enum class AddressType {
    // IPv4
    ThisNetwork, Private, SharedCgnat, Loopback, LinkLocal, IetfProtocol, Documentation, Benchmarking,
    Multicast, Reserved, LimitedBroadcast, Public,

    // IPv6 (Loopback, LinkLocal, Documentation, Multicast and Reserved are shared)
    Unspecified, Ipv4Mapped, Nat64, Teredo, SixToFour, UniqueLocal, GlobalUnicast,
}

/** Everything the calculator shows for an address and prefix. IPv4-only fields are null for IPv6. */
data class SubnetInfo(
    val address: IpAddress,
    val cidr: Cidr,
    val netmask: IpAddress?,
    val wildcard: IpAddress?,
    /** Null for /31 and /32 (RFC 3021: no broadcast) and for IPv6, which has none. */
    val broadcast: IpAddress?,
    val firstHost: IpAddress,
    val lastHost: IpAddress,
    val usableHosts: BigInteger,
    val totalAddresses: BigInteger,
    /** Historical class (A–E) of an IPv4 address. */
    val ipv4Class: Char?,
    val type: AddressType,
    /** DNS zone that holds the reverse records of this block (the enclosing octet/nibble boundary). */
    val reverseZone: String,
    /** IPv6 prefixes shorter than /64: how many /64 networks fit. */
    val subnets64: BigInteger?,
) {
    val version: IpVersion get() = address.version
}

object SubnetCalculator {

    fun info(address: IpAddress, prefix: Int): SubnetInfo {
        val cidr = Cidr.containing(address, prefix)
        return when (address.version) {
            IpVersion.V4 -> ipv4(address, cidr)
            IpVersion.V6 -> ipv6(address, cidr)
        }
    }

    private fun ipv4(address: IpAddress, cidr: Cidr): SubnetInfo {
        val prefix = cidr.prefix
        val (first, last, usable) = when (prefix) {
            32 -> Triple(cidr.first, cidr.first, BigInteger.ONE)
            31 -> Triple(cidr.first, cidr.last, TWO)
            else -> Triple(
                IpAddress(IpVersion.V4, cidr.first.value + BigInteger.ONE),
                IpAddress(IpVersion.V4, cidr.last.value - BigInteger.ONE),
                cidr.size - TWO,
            )
        }
        val firstOctet = address.value.toLong() shr 24
        return SubnetInfo(
            address = address,
            cidr = cidr,
            netmask = IpAddress(IpVersion.V4, Cidr.networkMask(IpVersion.V4, prefix)),
            wildcard = IpAddress(IpVersion.V4, Cidr.hostMask(IpVersion.V4, prefix)),
            broadcast = if (prefix <= 30) cidr.last else null,
            firstHost = first,
            lastHost = last,
            usableHosts = usable,
            totalAddresses = cidr.size,
            ipv4Class = when {
                firstOctet < 128 -> 'A'
                firstOctet < 192 -> 'B'
                firstOctet < 224 -> 'C'
                firstOctet < 240 -> 'D'
                else -> 'E'
            },
            type = typeOf(address),
            reverseZone = reverseZoneV4(cidr),
            subnets64 = null,
        )
    }

    private fun ipv6(address: IpAddress, cidr: Cidr): SubnetInfo = SubnetInfo(
        address = address,
        cidr = cidr,
        netmask = null,
        wildcard = null,
        broadcast = null,
        firstHost = cidr.first,
        lastHost = cidr.last,
        usableHosts = cidr.size,
        totalAddresses = cidr.size,
        ipv4Class = null,
        type = typeOf(address),
        reverseZone = reverseZoneV6(cidr),
        subnets64 = if (cidr.prefix < 64) BigInteger.ONE.shiftLeft(64 - cidr.prefix) else null,
    )

    /** "11000000.10101000.00000001.00001010" (IPv4 only). */
    fun binary(address: IpAddress): String {
        require(address.version == IpVersion.V4)
        val value = address.value.toLong()
        return (3 downTo 0).joinToString(".") { octet ->
            java.lang.Long.toBinaryString((value shr (octet * 8)) and 0xFF).padStart(8, '0')
        }
    }

    /** "0xC0A8010A" (IPv4) or the 32 hex digits of an IPv6 address. */
    fun hex(address: IpAddress): String = when (address.version) {
        IpVersion.V4 -> "0x" + String.format(Locale.ROOT, "%08X", address.value.toLong())
        IpVersion.V6 -> "0x" + address.value.toString(16).padStart(32, '0').uppercase(Locale.ROOT)
    }

    /** /24 → "1.168.192.in-addr.arpa"; a /26 lives in the zone of its /24. */
    fun reverseZoneV4(cidr: Cidr): String {
        val octets = cidr.prefix / 8
        val value = cidr.network.value.toLong()
        val labels = (0 until octets).map { ((value shr (24 - it * 8)) and 0xFF).toString() }.reversed()
        return (labels + "in-addr.arpa").joinToString(".")
    }

    /** /48 → 12 reversed nibbles + "ip6.arpa"; a /50 lives in the zone of its /48. */
    fun reverseZoneV6(cidr: Cidr): String {
        val nibbles = cidr.prefix / 4
        val hex = cidr.network.value.toString(16).padStart(32, '0')
        val labels = hex.take(nibbles).reversed().map { it.toString() }
        return (labels + "ip6.arpa").joinToString(".")
    }

    fun typeOf(address: IpAddress): AddressType {
        val match = SPECIAL[address.version].orEmpty().firstOrNull { (block, _) -> address in block }
        return match?.second ?: when (address.version) {
            IpVersion.V4 -> AddressType.Public
            IpVersion.V6 -> AddressType.Reserved
        }
    }

    private fun block(text: String): Cidr {
        val parsed = SubnetParser.parse(text) as ParsedSubnet.Ok
        return parsed.cidr
    }

    /** First match wins: more specific blocks come before the ones that contain them. */
    private val SPECIAL: Map<IpVersion, List<Pair<Cidr, AddressType>>> by lazy {
        mapOf(
            IpVersion.V4 to listOf(
                "255.255.255.255/32" to AddressType.LimitedBroadcast,
                "0.0.0.0/8" to AddressType.ThisNetwork,
                "10.0.0.0/8" to AddressType.Private,
                "100.64.0.0/10" to AddressType.SharedCgnat,
                "127.0.0.0/8" to AddressType.Loopback,
                "169.254.0.0/16" to AddressType.LinkLocal,
                "172.16.0.0/12" to AddressType.Private,
                "192.0.0.0/24" to AddressType.IetfProtocol,
                "192.0.2.0/24" to AddressType.Documentation,
                "192.168.0.0/16" to AddressType.Private,
                "198.18.0.0/15" to AddressType.Benchmarking,
                "198.51.100.0/24" to AddressType.Documentation,
                "203.0.113.0/24" to AddressType.Documentation,
                "224.0.0.0/4" to AddressType.Multicast,
                "240.0.0.0/4" to AddressType.Reserved,
            ).map { (text, type) -> block(text) to type },
            IpVersion.V6 to listOf(
                "::/128" to AddressType.Unspecified,
                "::1/128" to AddressType.Loopback,
                "::ffff:0:0/96" to AddressType.Ipv4Mapped,
                "64:ff9b::/96" to AddressType.Nat64,
                "2001::/32" to AddressType.Teredo,
                "2001:db8::/32" to AddressType.Documentation,
                "3fff::/20" to AddressType.Documentation,
                "2002::/16" to AddressType.SixToFour,
                "2000::/3" to AddressType.GlobalUnicast,
                "fc00::/7" to AddressType.UniqueLocal,
                "fe80::/10" to AddressType.LinkLocal,
                "ff00::/8" to AddressType.Multicast,
            ).map { (text, type) -> block(text) to type },
        )
    }
}
