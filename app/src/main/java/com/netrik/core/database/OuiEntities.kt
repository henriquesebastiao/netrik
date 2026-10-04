package com.netrik.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.netrik.core.oui.OuiRecord
import com.netrik.core.oui.OuiRegistry

@Entity(tableName = "oui_prefix")
data class OuiPrefixEntity(
    /** Uppercase hexadecimal of 6, 7 or 9 digits; unique across the three registries. */
    @PrimaryKey val prefix: String,
    val registry: String,
    val organization: String,
    val address: String?,
) {
    fun toRecord(): OuiRecord? = OuiRegistry.fromLabel(registry)?.let { OuiRecord(it, prefix, organization, address) }
}

fun OuiRecord.toEntity() = OuiPrefixEntity(prefix, registry.label, organization, address)

/** Metadata of the installed OUI database (single row). */
@Entity(tableName = "oui_meta")
data class OuiMetaEntity(
    @PrimaryKey val id: Int = 0,
    /** "bundled" (shipped in the APK) or "ieee" (downloaded by the user). */
    val source: String,
    /** Date of the data (ISO-8601, e.g. 2026-10-03). */
    @ColumnInfo(name = "data_date") val dataDate: String,
    @ColumnInfo(name = "prefix_count") val prefixCount: Int,
)

@Entity(tableName = "oui_history")
data class OuiHistoryEntity(
    /** Queried hexadecimal (prefix or full MAC), without separators. */
    @PrimaryKey val hex: String,
    @ColumnInfo(name = "queried_at") val queriedAt: Long,
)
