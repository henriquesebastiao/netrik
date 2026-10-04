package com.netrik.core.lan

/** De onde veio cada informação: mostrado na tela de detalhes, para nada parecer inventado. */
enum class InfoSource { Dns, Mdns, Netbios, Upnp, Oui }

/** Como o host foi detectado. */
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
    /** Só preenchido quando um protocolo da rede informou o MAC (o Android não expõe a tabela ARP). */
    val mac: Sourced? = null,
    val vendor: Sourced? = null,
    val model: String? = null,
    /** Tipos de serviço anunciados por mDNS (ex.: "_ipp._tcp"). */
    val services: Set<String> = emptySet(),
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
) {
    val ipValue: Long get() = ip.split('.').fold(0L) { acc, part -> (acc shl 8) or (part.toLongOrNull() ?: 0L) }
}

/** Atualização parcial vinda de uma das fontes de descoberta. */
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

/** Prioridade dos nomes: o DNS da rede é o mais confiável; o nome amigável do UPnP é o último recurso. */
private val NAME_PRIORITY = listOf(InfoSource.Dns, InfoSource.Mdns, InfoSource.Netbios, InfoSource.Upnp)

/** Fabricante pelo OUI (do MAC) vence o declarado pelo próprio dispositivo via UPnP. */
private val VENDOR_PRIORITY = listOf(InfoSource.Oui, InfoSource.Upnp)

private fun Sourced?.better(other: Sourced?, priority: List<InfoSource>): Sourced? = when {
    this == null -> other
    other == null -> this
    priority.indexOf(other.source) < priority.indexOf(source) -> other
    else -> this
}

/** Junta uma atualização ao que já se sabe do host, sem perder informação melhor. Lógica pura. */
fun LanDevice?.merge(update: DeviceUpdate): LanDevice {
    val base = this ?: LanDevice(ip = update.ip, detection = update.detection ?: Detection.Icmp)
    return base.copy(
        // ICMP/TCP dizem que o host respondeu agora; mantém a primeira detecção registrada.
        detection = this?.detection ?: base.detection,
        rttMs = base.rttMs ?: update.rttMs,
        hostname = base.hostname.better(update.hostname, NAME_PRIORITY),
        mac = base.mac ?: update.mac,
        vendor = base.vendor.better(update.vendor, VENDOR_PRIORITY),
        model = base.model ?: update.model,
        services = base.services + update.services,
    )
}

/** Filtro da busca: IP, MAC, fabricante ou hostname. */
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
