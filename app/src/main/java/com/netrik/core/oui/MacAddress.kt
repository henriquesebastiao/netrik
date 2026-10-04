package com.netrik.core.oui

/** Resultado da leitura de um MAC digitado em qualquer formato. [hex] vem em maiúsculas, sem separadores. */
sealed interface MacInput {
    val hex: String

    data object Empty : MacInput {
        override val hex = ""
    }

    /** Caractere que não é hexadecimal nem separador aceito. */
    data class InvalidChar(override val hex: String, val char: Char) : MacInput

    /** Mais de 12 dígitos: um MAC tem 6 octetos. */
    data class TooLong(override val hex: String) : MacInput

    /** Menos de 6 dígitos: não forma nem o prefixo OUI. */
    data class Partial(override val hex: String) : MacInput

    /** De 6 a 11 dígitos: prefixo consultável. */
    data class Prefix(override val hex: String) : MacInput

    /** MAC completo, 12 dígitos. */
    data class Full(override val hex: String) : MacInput

    val isQueryable: Boolean get() = this is Prefix || this is Full
}

object MacAddresses {

    private val separators = setOf(':', '-', '.', ' ', '\t')

    /**
     * Aceita "3C:22:FB:9A:10:7E", "3c-22-fb-9a-10-7e", "3C22.FB9A.107E", "3c22fb9a107e"
     * ou só o começo ("00-11-32").
     */
    fun parse(raw: String): MacInput {
        val text = raw.trim()
        if (text.isEmpty()) return MacInput.Empty
        val hex = StringBuilder()
        var invalid: Char? = null
        for (c in text) {
            when {
                c.isHexDigit() -> hex.append(c.uppercaseChar())
                c in separators -> Unit
                invalid == null -> invalid = c
            }
        }
        val digits = hex.toString()
        return when {
            invalid != null -> MacInput.InvalidChar(digits.take(12), invalid)
            digits.length > 12 -> MacInput.TooLong(digits.take(12))
            digits.length < 6 -> MacInput.Partial(digits)
            digits.length < 12 -> MacInput.Prefix(digits)
            else -> MacInput.Full(digits)
        }
    }

    /** MAC completo normalizado ("3C:22:FB:9A:10:7E") ou null se a entrada não for um MAC completo. */
    fun normalize(raw: String): String? = (parse(raw) as? MacInput.Full)?.let { format(it.hex) }

    /** "3C22FB9A107E" → "3C:22:FB:9A:10:7E"; prefixos ímpares ficam "C8:5C:E2:7". */
    fun format(hex: String): String = hex.chunked(2).joinToString(":")

    /** Bit U/L (2º bit menos significativo do 1º octeto): MAC aleatório ou definido por software. */
    fun isLocallyAdministered(hex: String): Boolean = firstOctetBit(hex, 0x02)

    /** Bit I/G (bit menos significativo do 1º octeto): endereço de grupo. */
    fun isMulticast(hex: String): Boolean = firstOctetBit(hex, 0x01)

    private fun firstOctetBit(hex: String, mask: Int): Boolean {
        val digit = hex.getOrNull(1)?.digitToIntOrNull(16) ?: return false
        return digit and mask != 0
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
