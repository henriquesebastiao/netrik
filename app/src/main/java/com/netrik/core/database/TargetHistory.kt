package com.netrik.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Targets recently used per tool ("ping", "traceroute", ...), for the history chips. */
@Entity(tableName = "target_history", primaryKeys = ["tool", "target"])
data class TargetHistoryEntity(
    val tool: String,
    val target: String,
    @ColumnInfo(name = "used_at") val usedAt: Long,
)

@Dao
interface TargetHistoryDao {
    @Query("SELECT * FROM target_history WHERE tool = :tool ORDER BY used_at DESC LIMIT :limit")
    fun observe(tool: String, limit: Int): Flow<List<TargetHistoryEntity>>

    @Upsert
    suspend fun upsert(entry: TargetHistoryEntity)

    @Query(
        "DELETE FROM target_history WHERE tool = :tool AND target NOT IN " +
            "(SELECT target FROM target_history WHERE tool = :tool ORDER BY used_at DESC LIMIT :keep)",
    )
    suspend fun trim(tool: String, keep: Int)

    @Query("DELETE FROM target_history WHERE tool = :tool")
    suspend fun clear(tool: String)
}

@Singleton
class TargetHistoryRepository @Inject constructor(
    private val dao: TargetHistoryDao,
    private val clock: Clock,
) {
    fun recent(tool: String): Flow<List<String>> = dao.observe(tool, LIMIT).map { list -> list.map { it.target } }

    suspend fun record(tool: String, target: String) {
        dao.upsert(TargetHistoryEntity(tool, target.trim(), clock.millis()))
        dao.trim(tool, LIMIT)
    }

    suspend fun clear(tool: String) = dao.clear(tool)

    private companion object {
        const val LIMIT = 8
    }
}
