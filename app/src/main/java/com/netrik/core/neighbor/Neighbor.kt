package com.netrik.core.neighbor

import java.util.Locale

/** Discovery protocol a neighbor answered on. */
enum class NeighborProtocol { Mndp, Ubiquiti }

/**
 * Device found by MNDP (MikroTik Neighbor Discovery) or the Ubiquiti discovery protocol. Every field is what
 * the device announced about itself; null = not announced. [sourceIp] is the address the packet came from.
 */
data class Neighbor(
    val protocol: NeighborProtocol,
    val sourceIp: String,
    val mac: String? = null,
    val ipv4: String? = null,
    val ipv6: String? = null,
    /** MNDP identity or Ubiquiti hostname. */
    val identity: String? = null,
    val version: String? = null,
    /** MNDP platform ("MikroTik"). */
    val platform: String? = null,
    /** MNDP board or Ubiquiti model. */
    val model: String? = null,
    val softwareId: String? = null,
    /** Interface of the device that sent the announcement (MNDP). */
    val interfaceName: String? = null,
    val uptimeSeconds: Long? = null,
    /** Full Ubiquiti firmware string. */
    val firmware: String? = null,
    /** Ubiquiti wireless network name. */
    val essid: String? = null,
    val lastSeenMillis: Long = 0,
) {
    /** Address to reach the device: the announced IPv4 or, without it, where the packet came from. */
    val address: String get() = ipv4 ?: sourceIp

    /** Same device across announcements: one per protocol and MAC (or source IP when there's no MAC). */
    val key: String get() = "${protocol.name}/${mac ?: sourceIp}"

    /** Fills the fields this announcement left out with what was already known. */
    fun mergedWith(older: Neighbor): Neighbor = copy(
        mac = mac ?: older.mac,
        ipv4 = ipv4 ?: older.ipv4,
        ipv6 = ipv6 ?: older.ipv6,
        identity = identity ?: older.identity,
        version = version ?: older.version,
        platform = platform ?: older.platform,
        model = model ?: older.model,
        softwareId = softwareId ?: older.softwareId,
        interfaceName = interfaceName ?: older.interfaceName,
        uptimeSeconds = uptimeSeconds ?: older.uptimeSeconds,
        firmware = firmware ?: older.firmware,
        essid = essid ?: older.essid,
    )
}

/** Keeps one entry per device, updating it on each new announcement. Pure logic. */
object NeighborList {
    fun upsert(list: List<Neighbor>, found: Neighbor): List<Neighbor> {
        val index = list.indexOfFirst { it.key == found.key }
        return if (index < 0) list + found else list.toMutableList().also { it[index] = found.mergedWith(list[index]) }
    }

    /** MikroTik first, then by name and address. */
    fun sorted(list: List<Neighbor>): List<Neighbor> = list.sortedWith(
        compareBy<Neighbor> { it.protocol.ordinal }
            .thenBy(nullsLast()) { it.identity?.lowercase() }
            .thenBy { ipSortKey(it.address) },
    )

    private fun ipSortKey(ip: String): Long =
        ip.split('.').takeIf { it.size == 4 }?.fold(0L) { acc, part -> (acc shl 8) or (part.toLongOrNull() ?: 0L) } ?: Long.MAX_VALUE
}

internal fun formatMac(bytes: ByteArray, offset: Int = 0): String =
    (offset until offset + 6).joinToString(":") { String.format(Locale.ROOT, "%02X", bytes[it].toInt() and 0xFF) }

internal fun isPlausibleMac(bytes: ByteArray, offset: Int = 0): Boolean =
    (offset until offset + 6).any { bytes[it].toInt() != 0 } && (offset until offset + 6).any { bytes[it].toInt() != -1 }

/** Text fields: UTF-8, without control characters (announcements are untrusted input). */
internal fun ByteArray.text(offset: Int, length: Int): String? =
    String(this, offset, length, Charsets.UTF_8)
        .filter { !it.isISOControl() }
        .trim()
        .take(MAX_TEXT)
        .ifEmpty { null }

private const val MAX_TEXT = 256
