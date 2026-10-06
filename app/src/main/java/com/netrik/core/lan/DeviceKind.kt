package com.netrik.core.lan

/** What a device probably is, guessed from ports only that kind of device opens or from what it announces via mDNS. */
enum class DeviceKind { Camera, Printer, Speaker, Tv, Cast, Iphone, Router, VoipPhone, Nas, SmartHome, Computer }

/** Why the kind was chosen: shown on the details screen, since it's an inference and not something the device said. */
sealed interface KindEvidence {
    data class Port(val port: Int) : KindEvidence
    data class Service(val type: String) : KindEvidence
}

data class KindGuess(val kind: DeviceKind, val evidence: KindEvidence)

/**
 * Pure rules, first match wins. Only ports that are close to a signature count (RTSP 554 → camera, JetDirect 9100 →
 * printer); generic ones (22, 80, 443, 445) say nothing about the device. The order settles devices that match more
 * than one rule: a Sonos also announces AirPlay, an Android TV also answers Cast, an Apple TV also opens 62078.
 */
object DeviceClassifier {

    private class Rule(
        val kind: DeviceKind,
        /** Any of these open is enough. */
        val ports: List<Int> = emptyList(),
        /** All of these open together (each one alone is too common). */
        val portsTogether: List<Int> = emptyList(),
        /** mDNS service type prefixes ("_ipp" matches "_ipp._tcp" and "_ipps._tcp"). */
        val services: List<String> = emptyList(),
    )

    private val RULES = listOf(
        // RTSP, RTSP alternate, Dahua and Xiongmai (XMEye) DVR/NVR protocols.
        Rule(DeviceKind.Camera, ports = listOf(554, 8554, 37777, 34567), services = listOf("_rtsp")),
        // JetDirect (raw printing), IPP, LPD.
        Rule(DeviceKind.Printer, ports = listOf(9100, 631, 515), services = listOf("_ipp", "_printer", "_pdl")),
        // Sonos control port.
        Rule(DeviceKind.Speaker, ports = listOf(1400), services = listOf("_sonos")),
        // Roku ECP, Android TV remote protocol; AirPlay receivers.
        Rule(DeviceKind.Tv, ports = listOf(8060, 6466, 6467), services = listOf("_androidtvremote", "_airplay", "_raop")),
        // Google Cast (Chromecast, Nest speakers and displays).
        Rule(DeviceKind.Cast, ports = listOf(8008, 8009), services = listOf("_googlecast")),
        // lockdownd (iTunes/Finder sync over Wi-Fi), only on iPhone and iPad.
        Rule(DeviceKind.Iphone, ports = listOf(62078)),
        // MikroTik Winbox and API, TR-069 CPE management.
        Rule(DeviceKind.Router, ports = listOf(8291, 8728, 8729, 7547)),
        Rule(DeviceKind.VoipPhone, ports = listOf(5060, 5061), services = listOf("_sip")),
        // Synology DSM (HTTP + HTTPS together), AFP, NFS, rsync; Time Machine and AFP shares.
        Rule(DeviceKind.Nas, ports = listOf(548, 2049, 873), portsTogether = listOf(5000, 5001), services = listOf("_afpovertcp", "_adisk", "_nfs")),
        // Home Assistant, MQTT brokers, Tuya and Yeelight local protocols; HomeKit, Matter, Hue bridges.
        Rule(DeviceKind.SmartHome, ports = listOf(8123, 1883, 8883, 6668, 55443), services = listOf("_home-assistant", "_hap", "_matter", "_hue")),
        // Windows RPC, Remote Desktop, VNC; workstation and file sharing announcements.
        Rule(DeviceKind.Computer, ports = listOf(135, 3389, 5900), services = listOf("_workstation", "_smb", "_companion", "_rfb")),
    )

    /** Signature ports outside the Top 100, checked too so the rules above can match. */
    val SIGNATURE_PORTS: List<Int> = RULES.flatMap { it.ports + it.portsTogether }.distinct()

    fun classify(openPorts: Set<Int>, services: Set<String>): KindGuess? {
        for (rule in RULES) {
            rule.ports.firstOrNull { it in openPorts }?.let { return KindGuess(rule.kind, KindEvidence.Port(it)) }
            if (rule.portsTogether.isNotEmpty() && openPorts.containsAll(rule.portsTogether)) {
                return KindGuess(rule.kind, KindEvidence.Port(rule.portsTogether.first()))
            }
            services.sorted().firstOrNull { type -> rule.services.any { type.startsWith(it) } }?.let {
                return KindGuess(rule.kind, KindEvidence.Service(it))
            }
        }
        return null
    }
}
