package com.netrik.core.wifi

/** Wi-Fi generation from `WifiInfo.getWifiStandard()` (API 30+). */
enum class WifiStandard(val generation: Int?) {
    Legacy(null), // 802.11a/b/g
    Wifi4(4), // 802.11n
    Wifi5(5), // 802.11ac
    Wifi6(6), // 802.11ax
    Wifi7(7), // 802.11be
    WiGig(null), // 802.11ad
    ;

    companion object {
        /** `ScanResult.WIFI_STANDARD_*` values. */
        fun fromAndroid(value: Int): WifiStandard? = when (value) {
            1 -> Legacy
            4 -> Wifi4
            5 -> Wifi5
            6 -> Wifi6
            7 -> WiGig
            8 -> Wifi7
            else -> null
        }
    }
}

/** One reading of the connected network's link. Null fields: Android didn't report them. */
data class WifiLinkSample(
    val timeMillis: Long,
    val rssiDbm: Int,
    val txMbps: Int?,
    val rxMbps: Int?,
    val frequencyMhz: Int?,
    val standard: WifiStandard?,
)

/** Last readings kept for the chart and min/average/max. Pure logic. */
data class SignalHistory(val samples: List<WifiLinkSample> = emptyList(), val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {

    fun add(sample: WifiLinkSample): SignalHistory =
        copy(samples = (samples + sample).filter { sample.timeMillis - it.timeMillis <= windowMillis })

    val latest: WifiLinkSample? get() = samples.lastOrNull()
    val min: Int? get() = samples.minOfOrNull { it.rssiDbm }
    val max: Int? get() = samples.maxOfOrNull { it.rssiDbm }
    val average: Int? get() = samples.takeIf { it.isNotEmpty() }?.let { list -> Math.round(list.map { it.rssiDbm }.average()).toInt() }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 120_000L
    }
}

object SignalMeter {
    /** dBm shown on the meter scale. */
    const val SCALE_MIN = -95
    const val SCALE_MAX = -30

    /** Position of [rssiDbm] on the meter, 0..1. */
    fun fraction(rssiDbm: Int): Float =
        ((rssiDbm - SCALE_MIN).toFloat() / (SCALE_MAX - SCALE_MIN)).coerceIn(0f, 1f)

    /** Beep spacing for the "Geiger counter" sound: faster as the signal gets better (1.5 s at −95 dBm, 0.15 s at −30). */
    fun beepIntervalMillis(rssiDbm: Int): Long {
        val f = fraction(rssiDbm)
        return (SLOWEST_BEEP_MS - f * (SLOWEST_BEEP_MS - FASTEST_BEEP_MS)).toLong()
    }

    private const val SLOWEST_BEEP_MS = 1_500f
    private const val FASTEST_BEEP_MS = 150f
}
