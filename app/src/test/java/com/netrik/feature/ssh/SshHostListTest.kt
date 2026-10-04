package com.netrik.feature.ssh

import com.netrik.core.database.KnownHostEntity
import com.netrik.core.database.SshDao
import com.netrik.core.database.SshGroupEntity
import com.netrik.core.database.SshHostEntity
import com.netrik.core.ssh.HostKey
import com.netrik.core.ssh.PrivateKeyFile
import com.netrik.core.ssh.SecretCipher
import com.netrik.core.ssh.SshAuth
import com.netrik.core.ssh.SshGroup
import com.netrik.core.ssh.SshHost
import com.netrik.core.ssh.SshHostDraft
import com.netrik.core.ssh.SshRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SshHostListTest {

    private fun host(id: Long, name: String, group: Long?, user: String = "root", address: String = "10.0.0.$id") =
        SshHost(id, name, address, 22, user, SshAuth.Password, group, null, null)

    private val prod = SshGroup(1, "Production", expanded = true)
    private val lab = SshGroup(2, "Lab", expanded = false)
    private val empty = SshGroup(3, "Clientes", expanded = true)
    private val hosts = listOf(
        host(1, "srv-01", 1, user = "admin"),
        host(2, "db-primary", 1, user = "postgres"),
        host(3, "nas", 2, address = "nas.lan"),
        host(4, "router", null),
    )

    @Test
    fun `groups in order and No group last`() {
        val groups = SshHostList.build(listOf(prod, lab, empty), hosts, "", noGroupExpanded = true)
        assertEquals(listOf(1L, 2L, 3L, null), groups.map { it.id })
        assertEquals(listOf(2, 1, 0, 1), groups.map { it.total })
        assertFalse(groups[1].expanded)
    }

    @Test
    fun `No group disappears when empty`() {
        val groups = SshHostList.build(listOf(prod), hosts.filter { it.groupId == 1L }, "", noGroupExpanded = true)
        assertEquals(listOf(1L), groups.map { it.id })
    }

    @Test
    fun `host of a group that no longer exists falls into No group`() {
        val groups = SshHostList.build(listOf(prod), listOf(host(9, "orphan", 42)), "", noGroupExpanded = true)
        assertEquals(1, groups.last { it.id == null }.total)
    }

    @Test
    fun `search by name, user or host expands the groups with results`() {
        val byUser = SshHostList.build(listOf(prod, lab, empty), hosts, "POSTGRES", noGroupExpanded = false)
        assertEquals(listOf(1L), byUser.map { it.id })
        assertEquals(listOf("db-primary"), byUser.single().hosts.map { it.name })
        assertEquals(2, byUser.single().total)

        val byHost = SshHostList.build(listOf(prod, lab, empty), hosts, "nas.lan", noGroupExpanded = false)
        assertTrue(byHost.single().expanded)

        assertTrue(SshHostList.build(listOf(prod), hosts, "inexistente", true).isEmpty())
    }

    // Repository with an in-memory DAO and a reversible test cipher.

    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = byteArrayOf(0x7F) + plain.map { (it.toInt() xor 0x5A).toByte() }
        override fun decrypt(sealed: ByteArray) = sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    private class MemoryDao : SshDao {
        val groups = MutableStateFlow(listOf<SshGroupEntity>())
        val hosts = MutableStateFlow(listOf<SshHostEntity>())
        val known = mutableMapOf<String, KnownHostEntity>()
        override fun observeGroups(): Flow<List<SshGroupEntity>> = groups.map { it.sortedBy(SshGroupEntity::position) }
        override fun observeHosts(): Flow<List<SshHostEntity>> = hosts
        override suspend fun host(id: Long) = hosts.value.firstOrNull { it.id == id }
        override suspend fun nextGroupPosition() = (groups.value.maxOfOrNull { it.position } ?: -1) + 1
        override suspend fun insertGroup(group: SshGroupEntity): Long {
            val id = (groups.value.maxOfOrNull { it.id } ?: 0) + 1
            groups.value += group.copy(id = id)
            return id
        }
        override suspend fun renameGroup(id: Long, name: String) { groups.value = groups.value.map { if (it.id == id) it.copy(name = name) else it } }
        override suspend fun setExpanded(id: Long, expanded: Boolean) { groups.value = groups.value.map { if (it.id == id) it.copy(expanded = expanded) else it } }
        override suspend fun setAllExpanded(expanded: Boolean) { groups.value = groups.value.map { it.copy(expanded = expanded) } }
        override suspend fun deleteGroup(id: Long) {
            groups.value = groups.value.filter { it.id != id }
            hosts.value = hosts.value.map { if (it.groupId == id) it.copy(groupId = null) else it } // SET NULL
        }
        override suspend fun insertHost(host: SshHostEntity): Long {
            val id = (hosts.value.maxOfOrNull { it.id } ?: 0) + 1
            hosts.value += host.copy(id = id)
            return id
        }
        override suspend fun updateHost(host: SshHostEntity) { hosts.value = hosts.value.map { if (it.id == host.id) host else it } }
        override suspend fun deleteHost(id: Long) { hosts.value = hosts.value.filter { it.id != id } }
        override suspend fun knownHost(hostId: String) = known[hostId]
        override suspend fun upsertKnownHost(entry: KnownHostEntity) { known[entry.hostId] = entry }
    }

    private fun draft(
        id: Long? = null,
        auth: SshAuth = SshAuth.Password,
        password: String? = "s3nha",
        key: PrivateKeyFile? = null,
        passphrase: String? = null,
        group: Long? = null,
    ) = SshHostDraft(id, "srv", "10.0.0.7", 22, "root", auth, group, password?.toByteArray(), key, passphrase?.toByteArray())

    private val clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `secrets go encrypted to the database and come back on connect`() = runTest {
        val dao = MemoryDao()
        val repo = SshRepository(dao, XorCipher(), clock, StandardTestDispatcher(testScheduler))
        val id = repo.save(draft())
        val stored = dao.host(id)!!
        assertFalse(String(stored.passwordEnc!!).contains("s3nha"))
        assertArrayEquals("s3nha".toByteArray(), repo.target(id)!!.password)
    }

    @Test
    fun `editing without typing the password keeps the saved one and switching to key discards it`() = runTest {
        val dao = MemoryDao()
        val repo = SshRepository(dao, XorCipher(), clock, StandardTestDispatcher(testScheduler))
        val id = repo.save(draft())
        repo.save(draft(id = id, password = null))
        assertArrayEquals("s3nha".toByteArray(), repo.target(id)!!.password)

        val key = PrivateKeyFile("id_ed25519", "key".toByteArray(), "ED25519", encrypted = true)
        repo.save(draft(id = id, auth = SshAuth.Key, password = null, key = key, passphrase = "frase"))
        val saved = dao.host(id)!!
        assertNull(saved.passwordEnc)
        assertEquals("id_ed25519", saved.keyName)
        assertEquals("ED25519 · 3 bytes", saved.keyInfo)
        val target = repo.target(id)!!
        assertArrayEquals("key".toByteArray(), target.privateKey)
        assertArrayEquals("frase".toByteArray(), target.keyPassphrase)
        assertNull(target.password)
        assertEquals(1, dao.hosts.value.size)
    }

    @Test
    fun `deleting a group moves the hosts to No group`() = runTest {
        val dao = MemoryDao()
        val repo = SshRepository(dao, XorCipher(), clock, StandardTestDispatcher(testScheduler))
        val group = repo.createGroup("  Lab ")
        assertEquals("Lab", dao.groups.value.single().name)
        val id = repo.save(draft(group = group))
        repo.deleteGroup(group)
        assertNull(repo.host(id)!!.groupId)
    }

    @Test
    fun `trusted host key is saved per host and port`() = runTest {
        val dao = MemoryDao()
        val repo = SshRepository(dao, XorCipher(), clock, StandardTestDispatcher(testScheduler))
        val key = HostKey("ssh-ed25519", byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".toByteArray())
        repo.trust("[10.0.0.7]:2222", key)
        assertEquals(key, repo.knownHost("[10.0.0.7]:2222"))
        assertNull(repo.knownHost("10.0.0.7"))
        assertEquals(key.fingerprint, dao.known.getValue("[10.0.0.7]:2222").fingerprint)
    }
}
