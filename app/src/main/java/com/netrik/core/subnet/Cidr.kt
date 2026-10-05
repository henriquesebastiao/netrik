package com.netrik.core.subnet

import java.math.BigInteger

/** A network block: [network] is always aligned to the prefix (host bits zeroed). */
data class Cidr(val network: IpAddress, val prefix: Int) : Comparable<Cidr> {

    init {
        require(prefix in 0..network.version.bits) { "Prefix outside 0..${network.version.bits}: $prefix" }
        require(network.value.and(hostMask(network.version, prefix)).signum() == 0) { "Host bits set in $network/$prefix" }
    }

    val version: IpVersion get() = network.version

    /** Number of addresses in the block (2^(bits - prefix)). */
    val size: BigInteger get() = BigInteger.ONE.shiftLeft(version.bits - prefix)

    val first: IpAddress get() = network
    val last: IpAddress get() = IpAddress(version, network.value + size - BigInteger.ONE)

    operator fun contains(address: IpAddress): Boolean =
        address.version == version && address.value >= network.value && address.value <= last.value

    override fun compareTo(other: Cidr): Int = compareValuesBy(this, other, { it.network }, { it.prefix })

    override fun toString(): String = "${network.format()}/$prefix"

    companion object {
        /** The block of [prefix] that contains [address] (host bits dropped). */
        fun containing(address: IpAddress, prefix: Int): Cidr =
            Cidr(IpAddress(address.version, address.value.and(networkMask(address.version, prefix))), prefix)

        /** Bits of the network part set: /24 → 255.255.255.0. */
        fun networkMask(version: IpVersion, prefix: Int): BigInteger =
            allOnes(version).xor(hostMask(version, prefix))

        /** Bits of the host part set: /24 → 0.0.0.255. */
        fun hostMask(version: IpVersion, prefix: Int): BigInteger = BigInteger.ONE.shiftLeft(version.bits - prefix) - BigInteger.ONE

        fun allOnes(version: IpVersion): BigInteger = BigInteger.ONE.shiftLeft(version.bits) - BigInteger.ONE

        /** "255.255.255.0" → 24; null when the mask isn't contiguous. */
        fun prefixOfMask(mask: IpAddress): Int? {
            if (mask.version != IpVersion.V4) return null
            val prefix = mask.value.bitCount()
            return prefix.takeIf { networkMask(IpVersion.V4, it) == mask.value }
        }

        /** Smallest number k with 2^k >= n (n >= 1). */
        fun bitsFor(n: BigInteger): Int = (n - BigInteger.ONE).bitLength()
    }
}

/** What went wrong with a typed network or address. */
enum class SubnetInputError { Empty, InvalidAddress, InvalidPrefix, InvalidMask }

sealed interface ParsedSubnet {
    /** [address] as typed (may have host bits) and the prefix; [prefixGiven] false = bare address (/32 or /128). */
    data class Ok(val address: IpAddress, val prefix: Int, val prefixGiven: Boolean) : ParsedSubnet {
        val cidr: Cidr get() = Cidr.containing(address, prefix)
    }

    data class Error(val error: SubnetInputError) : ParsedSubnet
}

object SubnetParser {
    /**
     * Accepts "192.168.1.10/24", "192.168.1.10/255.255.255.0", "192.168.1.10 255.255.255.0", "192.168.1.10 24",
     * "2001:db8::1/64" or a bare address (a single host: /32 or /128).
     */
    fun parse(text: String): ParsedSubnet {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ParsedSubnet.Error(SubnetInputError.Empty)
        val parts = trimmed.split('/', ' ', '\t').filter { it.isNotEmpty() }
        if (parts.size > 2) return ParsedSubnet.Error(SubnetInputError.InvalidAddress)
        val address = IpAddress.parse(parts[0]) ?: return ParsedSubnet.Error(SubnetInputError.InvalidAddress)
        if (parts.size == 1) return ParsedSubnet.Ok(address, address.version.bits, prefixGiven = false)
        val suffix = parts[1]
        val prefix = when {
            suffix.all { it in '0'..'9' } && suffix.length <= 3 -> suffix.toInt().takeIf { it <= address.version.bits }
                ?: return ParsedSubnet.Error(SubnetInputError.InvalidPrefix)
            address.version == IpVersion.V4 && '.' in suffix -> {
                val mask = IpAddress.parse(suffix) ?: return ParsedSubnet.Error(SubnetInputError.InvalidMask)
                Cidr.prefixOfMask(mask) ?: return ParsedSubnet.Error(SubnetInputError.InvalidMask)
            }
            else -> return ParsedSubnet.Error(SubnetInputError.InvalidPrefix)
        }
        return ParsedSubnet.Ok(address, prefix, prefixGiven = true)
    }
}
