package com.netrik.core.network.ping

import com.netrik.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject

data class ResolvedHost(val input: String, val address: String, val ipv6: Boolean)

interface HostResolver {
    /** Resolve um IP ou nome. Prefere IPv4 quando o nome tem os dois. Null = não resolvido. */
    suspend fun resolve(target: String): ResolvedHost?

    /** DNS reverso (PTR). Null quando não há nome ou ele é igual ao próprio IP. */
    suspend fun reverse(address: String): String?
}

class SystemHostResolver @Inject constructor(
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : HostResolver {

    override suspend fun resolve(target: String): ResolvedHost? = runInterruptible(io) {
        try {
            val all = InetAddress.getAllByName(target.trim())
            val chosen = all.firstOrNull { it is Inet4Address } ?: all.firstOrNull { it is Inet6Address } ?: return@runInterruptible null
            ResolvedHost(target.trim(), chosen.hostAddress.orEmpty().substringBefore('%'), chosen is Inet6Address)
        } catch (_: UnknownHostException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    override suspend fun reverse(address: String): String? = runInterruptible(io) {
        try {
            InetAddress.getByName(address).canonicalHostName.takeIf { it != address && it.isNotBlank() }
        } catch (_: UnknownHostException) {
            null
        }
    }
}

/** Validação local do texto do alvo: IPv4, IPv6 ou nome de host. */
object TargetValidator {
    private val hostname = Regex("""^(?=.{1,253}$)([A-Za-z0-9_](?:[A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?)(\.[A-Za-z0-9_](?:[A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?)*\.?$""")
    private val ipv6 = Regex("""^[0-9A-Fa-f:.]+$""")

    fun isValid(target: String): Boolean {
        val t = target.trim()
        if (t.isEmpty()) return false
        if (t.contains(':')) return ipv6.matches(t) && t.count { it == ':' } in 2..7
        return hostname.matches(t)
    }
}
