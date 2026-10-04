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

/** Why a connection failed, already in UI vocabulary. */
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
    /** First connection to this host:port: ask to confirm the fingerprint. */
    data class UnknownHostKey(val key: HostKey) : SshConnectResult
    /** The key changed since the last connection: possible man-in-the-middle attack. */
    data class ChangedHostKey(val stored: HostKey, val presented: HostKey) : SshConnectResult
    data class Failed(val failure: SshFailure) : SshConnectResult
}

/** Authenticated SSH session. The terminal opens channels on it. */
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
     * Connects and authenticates. The server key is checked against the trusted keys before
     * any credential is sent; if it is unknown or has changed, the connection is
     * closed and the result carries the key for the user to decide. The secrets of [target]
     * are wiped at the end.
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

        // Cancelling the coroutine (leaving the screen, "Cancel") drops the socket and unblocks connect.
        val handle = currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) session.disconnect() }
        return try {
            session.connect(TIMEOUT_MS)
            // Keeps the connection alive behind NAT/firewalls while the terminal sits idle.
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

    /** known_hosts with a single entry: the trusted key of this host:port (if any). */
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

    /** No interactive prompts: everything the user decides goes through the UI first. */
    private object NoPrompts : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?) = false
        override fun promptPassphrase(message: String?) = false
        override fun promptYesNo(message: String?) = false
        override fun showMessage(message: String?) = Unit
    }

    companion object {
        /** The design says "no reply after 10 s". */
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
