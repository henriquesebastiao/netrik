package com.netrik.core.ssh

import com.netrik.core.common.IoDispatcher
import com.netrik.core.database.KnownHostEntity
import com.netrik.core.database.SshDao
import com.netrik.core.database.SshHostEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Dados do formulário prontos para salvar. Segredos nulos mantêm os já salvos (edição). */
class SshHostDraft(
    val id: Long?,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val auth: SshAuth,
    val groupId: Long?,
    val password: ByteArray?,
    val key: PrivateKeyFile?,
    val keyPassphrase: ByteArray?,
)

@Singleton
class SshRepository @Inject constructor(
    private val dao: SshDao,
    private val cipher: SecretCipher,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    val groups: Flow<List<SshGroup>> = dao.observeGroups().map { list ->
        list.map { SshGroup(it.id, it.name, it.expanded) }
    }

    val hosts: Flow<List<SshHost>> = dao.observeHosts().map { list -> list.map { it.toModel() } }

    suspend fun host(id: Long): SshHost? = dao.host(id)?.toModel()

    /** Salva (novo ou edição) cifrando os segredos; devolve o id do host. */
    suspend fun save(draft: SshHostDraft): Long = withContext(io) {
        val existing = draft.id?.let { dao.host(it) }
        val usesPassword = draft.auth == SshAuth.Password
        val usesKey = draft.auth == SshAuth.Key
        val entity = SshHostEntity(
            id = existing?.id ?: 0,
            name = draft.name,
            host = draft.host,
            port = draft.port,
            username = draft.username,
            auth = draft.auth.storedName,
            groupId = draft.groupId,
            // Trocar o tipo de autenticação descarta o segredo do outro tipo.
            passwordEnc = if (!usesPassword) null else draft.password?.let(cipher::encrypt) ?: existing?.passwordEnc,
            keyEnc = if (!usesKey) null else draft.key?.bytes?.let(cipher::encrypt) ?: existing?.keyEnc,
            keyPassphraseEnc = when {
                !usesKey -> null
                // Chave nova: a senha vale para ela (vazia = sem senha).
                draft.key != null -> draft.keyPassphrase?.takeIf { it.isNotEmpty() }?.let(cipher::encrypt)
                draft.keyPassphrase != null && draft.keyPassphrase.isNotEmpty() -> cipher.encrypt(draft.keyPassphrase)
                else -> existing?.keyPassphraseEnc
            },
            keyName = if (!usesKey) null else draft.key?.name ?: existing?.keyName,
            keyInfo = if (!usesKey) null else draft.key?.info ?: existing?.keyInfo,
            createdAt = existing?.createdAt ?: clock.millis(),
        )
        if (existing == null) dao.insertHost(entity) else entity.id.also { dao.updateHost(entity) }
    }

    suspend fun deleteHost(id: Long) = dao.deleteHost(id)

    /** Indica se o host já tem senha / senha da chave salvas (para a edição). */
    suspend fun storedSecrets(id: Long): StoredSecrets {
        val entity = dao.host(id) ?: return StoredSecrets(password = false, keyPassphrase = false)
        return StoredSecrets(password = entity.passwordEnc != null, keyPassphrase = entity.keyPassphraseEnc != null)
    }

    /**
     * Monta o alvo de conexão de um host salvo, decifrando os segredos. [overrides] substitui
     * os segredos salvos (ex.: senha digitada agora no formulário).
     */
    suspend fun target(id: Long, overrides: SshHostDraft? = null): SshTarget? = withContext(io) {
        val entity = dao.host(id) ?: return@withContext null
        val auth = SshAuth.fromStored(entity.auth)
        SshTarget(
            host = entity.host,
            port = entity.port,
            username = entity.username,
            auth = auth,
            password = if (auth == SshAuth.Password) {
                overrides?.password?.copyOf() ?: entity.passwordEnc?.let(cipher::decrypt)
            } else {
                null
            },
            privateKey = if (auth == SshAuth.Key) {
                overrides?.key?.bytes?.copyOf() ?: entity.keyEnc?.let(cipher::decrypt)
            } else {
                null
            },
            keyPassphrase = if (auth == SshAuth.Key) {
                overrides?.keyPassphrase?.takeIf { it.isNotEmpty() }?.copyOf() ?: entity.keyPassphraseEnc?.let(cipher::decrypt)
            } else {
                null
            },
        )
    }

    suspend fun createGroup(name: String): Long = dao.appendGroup(name.trim())
    suspend fun renameGroup(id: Long, name: String) = dao.renameGroup(id, name.trim())
    suspend fun deleteGroup(id: Long) = dao.deleteGroup(id)
    suspend fun setExpanded(id: Long, expanded: Boolean) = dao.setExpanded(id, expanded)
    suspend fun setAllExpanded(expanded: Boolean) = dao.setAllExpanded(expanded)

    suspend fun knownHost(hostId: String): HostKey? =
        dao.knownHost(hostId)?.let { HostKey(it.keyType, it.keyBlob) }

    /** Confia na chave (novo host ou substituição explícita de uma chave alterada). */
    suspend fun trust(hostId: String, key: HostKey) = dao.upsertKnownHost(
        KnownHostEntity(hostId, key.type, key.blob, key.fingerprint, clock.millis()),
    )

    private fun SshHostEntity.toModel() = SshHost(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        auth = SshAuth.fromStored(auth),
        groupId = groupId,
        keyName = keyName,
        keyInfo = keyInfo,
    )
}

data class StoredSecrets(val password: Boolean, val keyPassphrase: Boolean)
