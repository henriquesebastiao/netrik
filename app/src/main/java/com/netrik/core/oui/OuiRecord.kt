package com.netrik.core.oui

/** Blocos de endereço do IEEE, do mais curto ao mais específico. */
enum class OuiRegistry(val label: String, val bits: Int, val hexDigits: Int) {
    MaL("MA-L", 24, 6),
    MaM("MA-M", 28, 7),
    MaS("MA-S", 36, 9),
    ;

    companion object {
        fun fromLabel(label: String): OuiRegistry? = entries.firstOrNull { it.label == label }

        /** Prefixos a tentar para um MAC, do mais específico ao mais curto. */
        fun candidatePrefixes(hex: String): List<String> =
            entries.sortedByDescending { it.hexDigits }
                .filter { hex.length >= it.hexDigits }
                .map { hex.take(it.hexDigits) }
    }
}

data class OuiRecord(
    val registry: OuiRegistry,
    /** Prefixo em hexadecimal maiúsculo: 6, 7 ou 9 dígitos. */
    val prefix: String,
    val organization: String,
    /** Null em registros privados, que o IEEE publica sem endereço. */
    val address: String?,
) {
    val isPrivate: Boolean get() = organization.equals("Private", ignoreCase = true)
}

/** Resultado de uma consulta: o registro mais específico que cobre o endereço. */
data class OuiMatch(val record: OuiRecord, val queriedHex: String)
