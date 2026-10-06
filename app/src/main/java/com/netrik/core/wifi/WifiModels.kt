package com.netrik.core.wifi

import com.netrik.core.network.WifiBand
import com.netrik.core.network.WifiChannels

/** Security announced by the network, from weakest to strongest. */
enum class WifiSecurity(val label: String, val weak: Boolean) {
    Open("Open", weak = true),
    Owe("OWE", weak = false),
    Wep("WEP", weak = true),
    Wpa("WPA", weak = true),
    Wpa2("WPA2", weak = false),
    Wpa2Wpa3("WPA2/WPA3", weak = false),
    Wpa3("WPA3", weak = false),
    Enterprise("Enterprise", weak = false),
    ;

    companion object {
        /**
         * Interpreta `ScanResult.capabilities`, ex.: "[WPA2-PSK-CCMP][RSN-SAE-CCMP][ESS][MFPC]".
         * WPA3-Personal shows up as SAE; Enhanced Open as OWE; Enterprise as EAP.
         */
        fun fromCapabilities(capabilities: String): WifiSecurity {
            val caps = capabilities.uppercase()
            val sae = "SAE" in caps
            val psk = "PSK" in caps
            return when {
                "EAP" in caps || "SUITE_B" in caps || "SUITE-B" in caps -> Enterprise
                sae && psk -> Wpa2Wpa3
                sae -> Wpa3
                "OWE" in caps -> Owe
                psk && ("RSN" in caps || "WPA2" in caps) -> Wpa2
                psk -> Wpa
                "WEP" in caps -> Wep
                else -> Open
            }
        }
    }
}

/** Quality bands from the design. */
enum class SignalQuality {
    Excellent,
    Good,
    Weak,
    VeryWeak,
    ;

    companion object {
        fun of(dbm: Int): SignalQuality = when {
            dbm >= -60 -> Excellent
            dbm >= -70 -> Good
            dbm >= -80 -> Weak
            else -> VeryWeak
        }
    }
}

/** A network seen in the scan, already interpreted. */
data class WifiNetwork(
    /** Null for a hidden network. */
    val ssid: String?,
    val bssid: String,
    val rssiDbm: Int,
    /** Frequency of the primary channel (where the beacon is). */
    val frequencyMhz: Int,
    val band: WifiBand?,
    val channel: Int?,
    val widthMhz: Int,
    /** Center of the whole channel (for 40/80/160 MHz it differs from the primary). */
    val centerMhz: Int,
    val security: WifiSecurity,
    val connected: Boolean = false,
    /** Vendor from the BSSID OUI; null if not found. */
    val vendor: String? = null,
    /** Locally administered BSSID (virtual AP, random MAC): has no IEEE vendor. */
    val bssidLocal: Boolean = false,
    /** Answers Wi-Fi RTT ranging (802.11mc, or 802.11az on Android 15+): the distance can be measured. */
    val rttResponder: Boolean = false,
) {
    val quality: SignalQuality get() = SignalQuality.of(rssiDbm)
}

object WifiChannelWidth {
    /** `ScanResult.CHANNEL_WIDTH_*` → MHz. 80+80 counts as 160. */
    fun toMhz(channelWidth: Int): Int = when (channelWidth) {
        0 -> 20
        1 -> 40
        2 -> 80
        3, 4 -> 160
        5 -> 320
        else -> 20
    }

    /** Center of the occupied channel: centerFreq0 applies to widths > 20 MHz when reported. */
    fun center(frequencyMhz: Int, widthMhz: Int, centerFreq0: Int): Int =
        if (widthMhz > 20 && centerFreq0 > 0) centerFreq0 else frequencyMhz
}

enum class WifiSort { Signal, Channel, Name }

/** Band filter and list sorting. The connected network stays out: it's shown highlighted. */
fun List<WifiNetwork>.nearby(bands: Set<WifiBand>, sort: WifiSort): List<WifiNetwork> =
    filter { !it.connected && it.band in bands }.sortedWith(
        when (sort) {
            WifiSort.Signal -> compareByDescending<WifiNetwork> { it.rssiDbm }
            WifiSort.Channel -> compareBy<WifiNetwork>({ it.band?.ordinal ?: Int.MAX_VALUE }, { it.channel ?: Int.MAX_VALUE }, { -it.rssiDbm })
            WifiSort.Name -> compareBy<WifiNetwork>({ it.ssid == null }, { it.ssid?.lowercase() }, { -it.rssiDbm })
        },
    )

internal fun bandAndChannel(frequencyMhz: Int): Pair<WifiBand?, Int?> =
    WifiChannels.bandOf(frequencyMhz) to WifiChannels.frequencyToChannel(frequencyMhz)

/** Drops networks that don't broadcast their name when [hide] is on; the connected one always stays. */
fun List<WifiNetwork>.withoutHidden(hide: Boolean): List<WifiNetwork> =
    if (!hide) this else filter { it.ssid != null || it.connected }
