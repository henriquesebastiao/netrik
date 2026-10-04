package com.netrik.core.oui

/** Result of reading a MAC typed in any format. [hex] is uppercase, without separators. */
sealed interface MacInput {
    val hex: String

    data object Empty : MacInput {
        override val hex = ""
    }

    /** Character that is neither hexadecimal nor an accepted separator. */
    data class InvalidChar(override val hex: String, val char: Char) : MacInput

    /** More than 12 digits: a MAC has 6 octets. */
    data class TooLong(override val hex: String) : MacInput

    /** Fewer than 6 digits: not even the OUI prefix. */
    data class Partial(override val hex: String) : MacInput

    /** 6 to 11 digits: a prefix that can be looked up. */
    data class Prefix(override val hex: String) : MacInput

    /** Full MAC, 12 digits. */
    data class Full(override val hex: String) : MacInput

    val isQueryable: Boolean get() = this is Prefix || this is Full
}

object MacAddresses {

    private val separators = setOf(':', '-', '.', ' ', '\t')

    /**
     * Aceita "3C:22:FB:9A:10:7E", "3c-22-fb-9a-10-7e", "3C22.FB9A.107E", "3c22fb9a107e"
     * or just the start ("00-11-32").
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

    /** Normalized full MAC ("3C:22:FB:9A:10:7E") or null if the input isn't a full MAC. */
    fun normalize(raw: String): String? = (parse(raw) as? MacInput.Full)?.let { format(it.hex) }

    /** "3C22FB9A107E" → "3C:22:FB:9A:10:7E"; odd prefixes become "C8:5C:E2:7". */
    fun format(hex: String): String = hex.chunked(2).joinToString(":")

    /** U/L bit (2nd least significant bit of the 1st octet): random or software-defined MAC. */
    fun isLocallyAdministered(hex: String): Boolean = firstOctetBit(hex, 0x02)

    /** I/G bit (least significant bit of the 1st octet): group address. */
    fun isMulticast(hex: String): Boolean = firstOctetBit(hex, 0x01)

    private fun firstOctetBit(hex: String, mask: Int): Boolean {
        val digit = hex.getOrNull(1)?.digitToIntOrNull(16) ?: return false
        return digit and mask != 0
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
