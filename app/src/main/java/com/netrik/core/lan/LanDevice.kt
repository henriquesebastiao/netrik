package com.netrik.core.lan

/** Where each piece of information came from: shown on the details screen, so nothing looks made up. */
enum class InfoSource { Dns, Mdns, Netbios, Upnp, Oui }

/** How the host was detected. */
sealed interface Detection {
    data object Icmp : Detection
    data class Tcp(val port: Int) : Detection
    data object Mdns : Detection
    data object Ssdp : Detection
    data object Netbios : Detection
}

data class Sourced(val value: String, val source: InfoSource)

data class LanDevice(
    val ip: String,
    val detection: Detection,
    val rttMs: Double? = null,
    val hostname: Sourced? = null,
    /** Only set when a network protocol reported the MAC (Android doesn't expose the ARP table). */
    val mac: Sourced? = null,
    val vendor: Sourced? = null,
    val model: String? = null,
    /** Service types announced via mDNS (e.g. "_ipp._tcp"). */
    val services: Set<String> = emptySet(),
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
) {
    val ipValue: Long get() = ip.split('.').fold(0L) { acc, part -> (acc shl 8) or (part.toLongOrNull() ?: 0L) }
}

/** Partial update coming from one of the discovery sources. */
data class DeviceUpdate(
    val ip: String,
    val detection: Detection? = null,
    val rttMs: Double? = null,
    val hostname: Sourced? = null,
    val mac: Sourced? = null,
    val vendor: Sourced? = null,
    val model: String? = null,
    val services: Set<String> = emptySet(),
)

/** Name priority: the network DNS is the most reliable; the UPnP friendly name is the last resort. */
private val NAME_PRIORITY = listOf(InfoSource.Dns, InfoSource.Mdns, InfoSource.Netbios, InfoSource.Upnp)

/** Vendor from the OUI (of the MAC) wins over the one declared by the device itself via UPnP. */
private val VENDOR_PRIORITY = listOf(InfoSource.Oui, InfoSource.Upnp)

private fun Sourced?.better(other: Sourced?, priority: List<InfoSource>): Sourced? = when {
    this == null -> other
    other == null -> this
    priority.indexOf(other.source) < priority.indexOf(source) -> other
    else -> this
}

/** Merges an update into what is already known about the host, without losing better information. Pure logic. */
fun LanDevice?.merge(update: DeviceUpdate): LanDevice {
    val base = this ?: LanDevice(ip = update.ip, detection = update.detection ?: Detection.Icmp)
    return base.copy(
        // ICMP/TCP say the host answered now; keeps the first detection recorded.
        detection = this?.detection ?: base.detection,
        rttMs = base.rttMs ?: update.rttMs,
        hostname = base.hostname.better(update.hostname, NAME_PRIORITY),
        mac = base.mac ?: update.mac,
        vendor = base.vendor.better(update.vendor, VENDOR_PRIORITY),
        model = base.model ?: update.model,
        services = base.services + update.services,
    )
}

/** Search filter: IP, MAC, vendor or hostname. */
fun LanDevice.matches(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val macPlain = mac?.value?.replace(":", "")?.lowercase()
    val qPlain = q.replace(":", "").replace("-", "")
    return ip.contains(q) ||
        mac?.value?.lowercase()?.contains(q) == true ||
        (macPlain != null && qPlain.length >= 2 && macPlain.contains(qPlain)) ||
        vendor?.value?.lowercase()?.contains(q) == true ||
        hostname?.value?.lowercase()?.contains(q) == true
}

enum class DeviceSort { Ip, Vendor }

fun List<LanDevice>.sortedFor(sort: DeviceSort): List<LanDevice> = when (sort) {
    DeviceSort.Ip -> sortedBy { it.ipValue }
    DeviceSort.Vendor -> sortedWith(compareBy<LanDevice>({ it.vendor == null }, { it.vendor?.value?.lowercase() }, { it.ipValue }))
}
