package com.netrik.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Port knocking group. "No group" isn't a row: it's the knocks with a null group_id. */
@Entity(tableName = "knock_group")
data class KnockGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val expanded: Boolean = true,
)

/** Saved knock sequence. Deleting the group moves its knocks to "No group" (SET NULL). */
@Entity(
    tableName = "knock_profile",
    foreignKeys = [
        ForeignKey(
            entity = KnockGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("group_id")],
)
data class KnockProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    @ColumnInfo(name = "delay_ms") val delayMs: Int,
    @ColumnInfo(name = "verify_port") val verifyPort: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** One knock of a sequence, in [position] order. "protocol" is "tcp", "udp" or "icmp". */
@Entity(
    tableName = "knock_step",
    foreignKeys = [
        ForeignKey(
            entity = KnockProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id")],
)
data class KnockStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    val position: Int,
    val protocol: String,
    val port: Int?,
    @ColumnInfo(name = "payload_size") val payloadSize: Int?,
)

@Dao
interface KnockDao {
    @Query("SELECT * FROM knock_group ORDER BY position, id")
    fun observeGroups(): Flow<List<KnockGroupEntity>>

    @Query("SELECT * FROM knock_profile ORDER BY name COLLATE NOCASE, id")
    fun observeProfiles(): Flow<List<KnockProfileEntity>>

    @Query("SELECT * FROM knock_step ORDER BY profile_id, position")
    fun observeSteps(): Flow<List<KnockStepEntity>>

    @Query("SELECT * FROM knock_profile WHERE id = :id")
    suspend fun profile(id: Long): KnockProfileEntity?

    @Query("SELECT * FROM knock_step WHERE profile_id = :profileId ORDER BY position")
    suspend fun steps(profileId: Long): List<KnockStepEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM knock_group")
    suspend fun nextGroupPosition(): Int

    @Insert
    suspend fun insertGroup(group: KnockGroupEntity): Long

    @Query("UPDATE knock_group SET name = :name WHERE id = :id")
    suspend fun renameGroup(id: Long, name: String)

    @Query("UPDATE knock_group SET expanded = :expanded WHERE id = :id")
    suspend fun setExpanded(id: Long, expanded: Boolean)

    @Query("UPDATE knock_group SET expanded = :expanded")
    suspend fun setAllExpanded(expanded: Boolean)

    @Query("DELETE FROM knock_group WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Insert
    suspend fun insertProfile(profile: KnockProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: KnockProfileEntity)

    @Query("UPDATE knock_profile SET group_id = :groupId WHERE id = :id")
    suspend fun moveProfile(id: Long, groupId: Long?)

    @Query("DELETE FROM knock_profile WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Insert
    suspend fun insertSteps(steps: List<KnockStepEntity>)

    @Query("DELETE FROM knock_step WHERE profile_id = :profileId")
    suspend fun deleteSteps(profileId: Long)

    /** Creates the group at the end of the list (before "No group", which is always last). */
    @Transaction
    suspend fun appendGroup(name: String): Long =
        insertGroup(KnockGroupEntity(name = name, position = nextGroupPosition()))

    /** Inserts or updates the profile and replaces its steps; returns the profile id. */
    @Transaction
    suspend fun saveProfile(profile: KnockProfileEntity, steps: List<KnockStepEntity>): Long {
        val id = if (profile.id == 0L) insertProfile(profile) else profile.id.also { updateProfile(profile) }
        deleteSteps(id)
        insertSteps(steps.mapIndexed { index, step -> step.copy(id = 0, profileId = id, position = index) })
        return id
    }

    /**
     * Applies an import in one transaction: creates [newGroups] and inserts each profile with its steps.
     * [groupOf] gives each profile's group name (null = no group); existing groups are matched by name, ignoring case.
     */
    @Transaction
    suspend fun importAll(
        newGroups: List<String>,
        profiles: List<Pair<KnockProfileEntity, List<KnockStepEntity>>>,
        groupOf: List<String?>,
    ) {
        newGroups.forEach { appendGroup(it) }
        val ids = groups().associate { it.name.trim().lowercase() to it.id }
        profiles.forEachIndexed { index, (profile, steps) ->
            val groupId = groupOf[index]?.let { ids[it.trim().lowercase()] }
            saveProfile(profile.copy(id = 0, groupId = groupId), steps)
        }
    }

    @Query("SELECT * FROM knock_group ORDER BY position, id")
    suspend fun groups(): List<KnockGroupEntity>
}
