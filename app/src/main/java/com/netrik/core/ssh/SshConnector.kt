package com.netrik.core.ssh

import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchAlgoNegoFailException
import com.jcraft.jsch.JSchChangedHostKeyException
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.JSchUnknownHostKeyException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.netrik.core.common.IoDispatcher
import com.netrik.core.network.LocalAddress
import com.netrik.core.network.LocalNetworkAccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** Por que uma conexão falhou, já no vocabulário da interface. */
sealed interface SshFailure {
    data object Timeout : SshFailure
    data object Refused : SshFailure
    data object HostNotFound : SshFailure
    data object NoRoute : SshFailure
    data object AuthRejected : SshFailure
    data object KeyPassphraseRequired : SshFailure
    data object KeyWrongPassphrase : SshFailure
    data object KeyInvalid : SshFailure
    data object LocalNetworkPermission : SshFailure
    data class NoCommonAlgorithm(val detail: String?) : SshFailure
    data class Other(val detail: String?) : SshFailure
}

sealed interface SshConnectResult {
    class Connected(val session: SshSession) : SshConnectResult
    /** Primeira conexão a este host:porta: pedir confirmação da impressão digital. */
    data class UnknownHostKey(val key: HostKey) : SshConnectResult
    /** A chave mudou desde a última conexão: possível ataque man-in-the-middle. */
    data class ChangedHostKey(val stored: HostKey, val presented: HostKey) : SshConnectResult
    data class Failed(val failure: SshFailure) : SshConnectResult
}

/** Sessão SSH autenticada. O terminal (Etapa 7) abre canais nela. */
class SshSession internal constructor(internal val session: Session, val hostKey: HostKey?) {
    val serverVersion: String? get() = session.serverVersion
    val isConnected: Boolean get() = session.isConnected
    fun close() = session.disconnect()
}

@Singleton
class SshConnector @Inject constructor(
    private val repository: SshRepository,
    private val localNetwork: LocalNetworkAccess,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Conecta e autentica. A chave do servidor é conferida com as chaves confiadas antes de
     * qualquer credencial ser enviada; se for desconhecida ou tiver mudado, a conexão é
     * encerrada e o resultado traz a chave para o usuário decidir. Os segredos de [target]
     * são apagados ao final.
     */
    suspend fun connect(target: SshTarget): SshConnectResult = withContext(io) {
        try {
            connectInternal(target)
        } finally {
            target.wipe()
        }
    }

    private suspend fun connectInternal(target: SshTarget): SshConnectResult {
        if (target.auth == SshAuth.Key) {
            val key = target.privateKey ?: return SshConnectResult.Failed(SshFailure.KeyInvalid)
            when (val inspection = PrivateKeys.inspect(key, target.keyPassphrase)) {
                KeyInspection.Invalid, KeyInspection.TooLarge -> return SshConnectResult.Failed(SshFailure.KeyInvalid)
                KeyInspection.WrongPassphrase -> return SshConnectResult.Failed(SshFailure.KeyWrongPassphrase)
                is KeyInspection.Valid -> if (inspection.encrypted && (target.keyPassphrase == null || target.keyPassphrase.isEmpty())) {
                    return SshConnectResult.Failed(SshFailure.KeyPassphraseRequired)
                }
            }
        }

        val address = try {
            InetAddress.getAllByName(target.host).first()
        } catch (_: UnknownHostException) {
            return SshConnectResult.Failed(SshFailure.HostNotFound)
        }
        val ip = address.hostAddress ?: return SshConnectResult.Failed(SshFailure.HostNotFound)
        if (LocalAddress.isLocal(ip) && !localNetwork.isGranted()) {
            return SshConnectResult.Failed(SshFailure.LocalNetworkPermission)
        }

        JschSetup.install()
        val jsch = JSch()
        val stored = repository.knownHost(target.hostId)
        val hostKeys = SingleHostKeyRepository(stored)
        jsch.hostKeyRepository = hostKeys
        if (target.auth == SshAuth.Key) {
            jsch.addIdentity(target.username, target.privateKey!!.copyOf(), null, target.keyPassphrase?.copyOf())
        }
        val session = jsch.getSession(target.username, ip, target.port)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig(
            "PreferredAuthentications",
            if (target.auth == SshAuth.Key) "publickey" else "password,keyboard-interactive",
        )
        target.password?.let { session.setPassword(it.copyOf()) }
        session.userInfo = NoPrompts

        // Cancelar a coroutine (sair da tela, "Cancelar") derruba o socket e destrava o connect.
        val handle = currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) session.disconnect() }
        return try {
            session.connect(TIMEOUT_MS)
            // Mantém a conexão viva atrás de NAT/firewall enquanto o terminal fica parado.
            session.setServerAliveInterval(KEEPALIVE_MS)
            SshConnectResult.Connected(SshSession(session, hostKeys.presented))
        } catch (e: JSchException) {
            session.disconnect()
            val presented = hostKeys.presented
            when {
                e is JSchUnknownHostKeyException && presented != null -> SshConnectResult.UnknownHostKey(presented)
                e is JSchChangedHostKeyException && presented != null && stored != null ->
                    SshConnectResult.ChangedHostKey(stored, presented)
                else -> SshConnectResult.Failed(classify(e))
            }
        } finally {
            handle.dispose()
        }
    }

    /** known_hosts com uma única entrada: a chave confiada deste host:porta (se houver). */
    private class SingleHostKeyRepository(private val stored: HostKey?) : HostKeyRepository {
        @Volatile var presented: HostKey? = null

        override fun check(host: String?, key: ByteArray): Int {
            val type = HostKeys.typeOf(key) ?: return HostKeyRepository.NOT_INCLUDED
            val current = HostKey(type, key.copyOf())
            presented = current
            return when (HostKeys.status(stored, current)) {
                HostKeyStatus.Known -> HostKeyRepository.OK
                HostKeyStatus.Unknown -> HostKeyRepository.NOT_INCLUDED
                HostKeyStatus.Changed -> HostKeyRepository.CHANGED
            }
        }

        override fun add(hostkey: com.jcraft.jsch.HostKey?, ui: UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "netrik"
        override fun getHostKey(): Array<com.jcraft.jsch.HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<com.jcraft.jsch.HostKey> = emptyArray()
    }

    /** Sem perguntas interativas: tudo o que o usuário decide passa pela interface antes. */
    private object NoPrompts : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?) = false
        override fun promptPassphrase(message: String?) = false
        override fun promptYesNo(message: String?) = false
        override fun showMessage(message: String?) = Unit
    }

    companion object {
        /** O design fala em "sem resposta após 10 s". */
        const val TIMEOUT_MS = 10_000
        private const val KEEPALIVE_MS = 30_000

        fun classify(e: Throwable): SshFailure {
            val cause = generateSequence(e) { it.cause }.drop(1).firstOrNull()
            val message = e.message.orEmpty()
            return when {
                cause is SocketTimeoutException || message.contains("timeout", ignoreCase = true) -> SshFailure.Timeout
                cause is ConnectException -> SshFailure.Refused
                cause is NoRouteToHostException -> SshFailure.NoRoute
                cause is UnknownHostException -> SshFailure.HostNotFound
                message.startsWith("Auth fail") || message.startsWith("Auth cancel") ->
                    SshFailure.AuthRejected
                e is JSchAlgoNegoFailException -> SshFailure.NoCommonAlgorithm(e.message)
                else -> SshFailure.Other(e.message ?: cause?.message)
            }
        }
    }
}
