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
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** SSH host group. "No group" isn't a row: it's the hosts with a null group_id. */
@Entity(tableName = "ssh_group")
data class SshGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val expanded: Boolean = true,
)

/**
 * Saved SSH host. Password, private key and key passphrase are encrypted with the Android Keystore
 * ([com.netrik.core.ssh.SecretCipher]); the database never stores these secrets in plain text.
 * Deleting the group moves its hosts to "No group" (SET NULL).
 */
@Entity(
    tableName = "ssh_host",
    foreignKeys = [
        ForeignKey(
            entity = SshGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("group_id")],
)
data class SshHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    /** "password" or "key". */
    val auth: String,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    @ColumnInfo(name = "password_enc") val passwordEnc: ByteArray?,
    @ColumnInfo(name = "key_enc") val keyEnc: ByteArray?,
    @ColumnInfo(name = "key_passphrase_enc") val keyPassphraseEnc: ByteArray?,
    /** Name of the imported key file and the summary shown ("ED25519 · 411 bytes"). */
    @ColumnInfo(name = "key_name") val keyName: String?,
    @ColumnInfo(name = "key_info") val keyInfo: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Trusted host key (same idea as OpenSSH's known_hosts). */
@Entity(tableName = "ssh_known_host")
data class KnownHostEntity(
    /** "host" or "[host]:port". */
    @PrimaryKey @ColumnInfo(name = "host_id") val hostId: String,
    @ColumnInfo(name = "key_type") val keyType: String,
    @ColumnInfo(name = "key_blob") val keyBlob: ByteArray,
    val fingerprint: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Dao
interface SshDao {
    @Query("SELECT * FROM ssh_group ORDER BY position, id")
    fun observeGroups(): Flow<List<SshGroupEntity>>

    @Query("SELECT * FROM ssh_host ORDER BY name COLLATE NOCASE, id")
    fun observeHosts(): Flow<List<SshHostEntity>>

    @Query("SELECT * FROM ssh_host WHERE id = :id")
    suspend fun host(id: Long): SshHostEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM ssh_group")
    suspend fun nextGroupPosition(): Int

    @Insert
    suspend fun insertGroup(group: SshGroupEntity): Long

    @Query("UPDATE ssh_group SET name = :name WHERE id = :id")
    suspend fun renameGroup(id: Long, name: String)

    @Query("UPDATE ssh_group SET expanded = :expanded WHERE id = :id")
    suspend fun setExpanded(id: Long, expanded: Boolean)

    @Query("UPDATE ssh_group SET expanded = :expanded")
    suspend fun setAllExpanded(expanded: Boolean)

    @Query("DELETE FROM ssh_group WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Insert
    suspend fun insertHost(host: SshHostEntity): Long

    @Update
    suspend fun updateHost(host: SshHostEntity)

    @Query("DELETE FROM ssh_host WHERE id = :id")
    suspend fun deleteHost(id: Long)

    @Query("SELECT * FROM ssh_known_host WHERE host_id = :hostId")
    suspend fun knownHost(hostId: String): KnownHostEntity?

    @Upsert
    suspend fun upsertKnownHost(entry: KnownHostEntity)

    /** Creates the group at the end of the list (before "No group", which is always last). */
    @Transaction
    suspend fun appendGroup(name: String): Long =
        insertGroup(SshGroupEntity(name = name, position = nextGroupPosition()))
}
