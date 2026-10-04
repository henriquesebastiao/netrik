package com.netrik.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.netrik.core.oui.OuiRecord
import com.netrik.core.oui.OuiRegistry

@Entity(tableName = "oui_prefix")
data class OuiPrefixEntity(
    /** Hexadecimal maiúsculo de 6, 7 ou 9 dígitos; único entre os três registros. */
    @PrimaryKey val prefix: String,
    val registry: String,
    val organization: String,
    val address: String?,
) {
    fun toRecord(): OuiRecord? = OuiRegistry.fromLabel(registry)?.let { OuiRecord(it, prefix, organization, address) }
}

fun OuiRecord.toEntity() = OuiPrefixEntity(prefix, registry.label, organization, address)

/** Metadados da base OUI instalada (linha única). */
@Entity(tableName = "oui_meta")
data class OuiMetaEntity(
    @PrimaryKey val id: Int = 0,
    /** "bundled" (embarcada no APK) ou "ieee" (baixada pelo usuário). */
    val source: String,
    /** Data dos dados (ISO-8601, ex.: 2026-10-03). */
    @ColumnInfo(name = "data_date") val dataDate: String,
    @ColumnInfo(name = "prefix_count") val prefixCount: Int,
)

@Entity(tableName = "oui_history")
data class OuiHistoryEntity(
    /** Hexadecimal consultado (prefixo ou MAC completo), sem separadores. */
    @PrimaryKey val hex: String,
    @ColumnInfo(name = "queried_at") val queriedAt: Long,
)
