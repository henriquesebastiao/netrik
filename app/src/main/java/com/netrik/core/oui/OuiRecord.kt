package com.netrik.core.oui

/** IEEE address blocks, from the shortest to the most specific. */
enum class OuiRegistry(val label: String, val bits: Int, val hexDigits: Int) {
    MaL("MA-L", 24, 6),
    MaM("MA-M", 28, 7),
    MaS("MA-S", 36, 9),
    ;

    companion object {
        fun fromLabel(label: String): OuiRegistry? = entries.firstOrNull { it.label == label }

        /** Prefixes to try for a MAC, from the most specific to the shortest. */
        fun candidatePrefixes(hex: String): List<String> =
            entries.sortedByDescending { it.hexDigits }
                .filter { hex.length >= it.hexDigits }
                .map { hex.take(it.hexDigits) }
    }
}

data class OuiRecord(
    val registry: OuiRegistry,
    /** Prefix in uppercase hexadecimal: 6, 7 or 9 digits. */
    val prefix: String,
    val organization: String,
    /** Null for private registrations, which the IEEE publishes without an address. */
    val address: String?,
) {
    val isPrivate: Boolean get() = organization.equals("Private", ignoreCase = true)
}

/** Lookup result: the most specific registration covering the address. */
data class OuiMatch(val record: OuiRecord, val queriedHex: String)
