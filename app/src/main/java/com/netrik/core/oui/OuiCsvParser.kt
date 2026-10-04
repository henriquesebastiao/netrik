package com.netrik.core.oui

import java.io.IOException
import java.io.Reader

/** Arquivo OUI com formato inesperado (cabeçalho errado, poucos registros). */
class OuiFormatException(message: String) : IOException(message)

/**
 * Lê os CSVs públicos do IEEE (oui.csv, mam.csv, oui36.csv), no formato
 * `Registry,Assignment,Organization Name,Organization Address`, com campos entre aspas (RFC 4180).
 */
object OuiCsvParser {

    private val header = listOf("Registry", "Assignment", "Organization Name", "Organization Address")
    private val whitespace = Regex("\\s+")

    /** Lança [OuiFormatException] se o arquivo não tiver o cabeçalho esperado do registro [expected]. */
    fun parse(reader: Reader, expected: OuiRegistry): List<OuiRecord> {
        val rows = readRows(reader)
        val head = rows.firstOrNull()?.map { it.trim().removePrefix("\uFEFF") }
        if (head != header) throw OuiFormatException("Cabeçalho inesperado no arquivo ${expected.label}")
        return rows.drop(1).mapNotNull { row -> toRecord(row, expected) }
    }

    private fun toRecord(row: List<String>, expected: OuiRegistry): OuiRecord? {
        if (row.size < 3) return null
        val registry = OuiRegistry.fromLabel(row[0].trim()) ?: return null
        if (registry != expected) return null
        val prefix = row[1].trim().uppercase()
        if (prefix.length != registry.hexDigits || prefix.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
        val organization = row[2].clean().ifEmpty { return null }
        val address = row.getOrNull(3)?.clean()?.takeIf { it.isNotEmpty() }
        return OuiRecord(registry, prefix, organization, address)
    }

    private fun String.clean() = replace(whitespace, " ").trim()

    /** Parser CSV mínimo: aspas, aspas escapadas ("") e quebras de linha dentro de campos. */
    internal fun readRows(reader: Reader): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var c = reader.read()
        while (c != -1) {
            val ch = c.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    val next = reader.read()
                    if (next == '"'.code) {
                        field.append('"')
                    } else {
                        inQuotes = false
                        c = next
                        continue
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> { row.add(field.toString()); field.clear() }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(field.toString()); field.clear()
                        if (row.any { it.isNotEmpty() }) rows.add(row)
                        row = mutableListOf()
                    }
                    else -> field.append(ch)
                }
            }
            c = reader.read()
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotEmpty() }) rows.add(row)
        }
        return rows
    }
}
