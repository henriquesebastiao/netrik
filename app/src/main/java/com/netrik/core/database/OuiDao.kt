package com.netrik.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OuiDao {

    /** O prefixo mais longo vence (MA-S > MA-M > MA-L). */
    @Query("SELECT * FROM oui_prefix WHERE prefix IN (:candidates) ORDER BY LENGTH(prefix) DESC LIMIT 1")
    suspend fun findBestMatch(candidates: List<String>): OuiPrefixEntity?

    @Query("SELECT * FROM oui_prefix WHERE prefix IN (:prefixes)")
    suspend fun findAll(prefixes: List<String>): List<OuiPrefixEntity>

    @Query("SELECT prefix FROM oui_prefix")
    suspend fun allPrefixes(): List<String>

    @Query("SELECT * FROM oui_meta WHERE id = 0")
    fun observeMeta(): Flow<OuiMetaEntity?>

    @Query("SELECT * FROM oui_meta WHERE id = 0")
    suspend fun meta(): OuiMetaEntity?

    @Query("DELETE FROM oui_prefix")
    suspend fun clearPrefixes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrefixes(prefixes: List<OuiPrefixEntity>)

    @Upsert
    suspend fun upsertMeta(meta: OuiMetaEntity)

    /** Troca a base inteira numa transação: ou entra tudo, ou nada muda. */
    @Transaction
    suspend fun replaceAll(prefixes: List<OuiPrefixEntity>, meta: OuiMetaEntity) {
        clearPrefixes()
        prefixes.chunked(INSERT_CHUNK).forEach { insertPrefixes(it) }
        upsertMeta(meta)
    }

    @Query("SELECT * FROM oui_history ORDER BY queried_at DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<OuiHistoryEntity>>

    @Upsert
    suspend fun upsertHistory(entry: OuiHistoryEntity)

    @Query("DELETE FROM oui_history WHERE hex NOT IN (SELECT hex FROM oui_history ORDER BY queried_at DESC LIMIT :keep)")
    suspend fun trimHistory(keep: Int)

    @Query("DELETE FROM oui_history WHERE hex = :hex")
    suspend fun deleteHistory(hex: String)

    @Query("DELETE FROM oui_history")
    suspend fun clearHistory()
}

private const val INSERT_CHUNK = 2_000
