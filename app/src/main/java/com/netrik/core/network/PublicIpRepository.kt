package com.netrik.core.network

import com.netrik.core.common.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

interface PublicIpRepository {
    /** Consulta o IP público num serviço externo. Só deve ser chamada por ação do usuário. */
    suspend fun fetchPublicIp(): Result<String>
}

class IpifyPublicIpRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PublicIpRepository {

    override suspend fun fetchPublicIp(): Result<String> = try {
        Result.success(withContext(ioDispatcher) { request() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.failure(e)
    }

    private fun request(): String {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            return body.takeIf(::looksLikeIpAddress) ?: throw IOException("Resposta inesperada")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.ipify.org"
        const val TIMEOUT_MS = 5_000
    }
}

/** Aceita IPv4 válido ou um IPv6 plausível (hexadecimal e dois-pontos). */
internal fun looksLikeIpAddress(value: String): Boolean =
    Ipv4.parse(value) != null ||
        (value.contains(':') && value.length <= 45 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' })
