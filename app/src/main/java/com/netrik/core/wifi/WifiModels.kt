package com.netrik.core.wifi

import com.netrik.core.network.WifiBand
import com.netrik.core.network.WifiChannels

/** Segurança anunciada pela rede, do mais fraco ao mais forte. */
enum class WifiSecurity(val label: String, val weak: Boolean) {
    Open("Aberta", weak = true),
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
         * WPA3-Personal aparece como SAE; Enhanced Open como OWE; Enterprise como EAP.
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

/** Faixas de qualidade do design. */
enum class SignalQuality(val label: String) {
    Excellent("Ótimo"),
    Good("Bom"),
    Weak("Fraco"),
    VeryWeak("Muito fraco"),
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

/** Uma rede vista no scan, já interpretada. */
data class WifiNetwork(
    /** Null para rede oculta. */
    val ssid: String?,
    val bssid: String,
    val rssiDbm: Int,
    /** Frequência do canal primário (onde está o beacon). */
    val frequencyMhz: Int,
    val band: WifiBand?,
    val channel: Int?,
    val widthMhz: Int,
    /** Centro do canal inteiro (para 40/80/160 MHz difere do primário). */
    val centerMhz: Int,
    val security: WifiSecurity,
    val connected: Boolean = false,
    /** Fabricante pelo OUI do BSSID; null se não encontrado. */
    val vendor: String? = null,
    /** BSSID administrado localmente (AP virtual, MAC aleatório): não tem fabricante no IEEE. */
    val bssidLocal: Boolean = false,
) {
    val quality: SignalQuality get() = SignalQuality.of(rssiDbm)
}

object WifiChannelWidth {
    /** `ScanResult.CHANNEL_WIDTH_*` → MHz. 80+80 conta como 160. */
    fun toMhz(channelWidth: Int): Int = when (channelWidth) {
        0 -> 20
        1 -> 40
        2 -> 80
        3, 4 -> 160
        5 -> 320
        else -> 20
    }

    /** Centro do canal ocupado: centerFreq0 vale para larguras > 20 MHz quando informado. */
    fun center(frequencyMhz: Int, widthMhz: Int, centerFreq0: Int): Int =
        if (widthMhz > 20 && centerFreq0 > 0) centerFreq0 else frequencyMhz
}

enum class WifiSort { Signal, Channel, Name }

/** Filtro de bandas e ordenação da lista. Rede conectada fica fora: aparece em destaque. */
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
