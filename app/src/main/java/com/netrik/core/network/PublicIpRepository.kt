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
    /** Queries the public IP on an external service. Must only be called by a user action. */
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

/** Accepts a valid IPv4 or a plausible IPv6 (hexadecimal and colons). */
internal fun looksLikeIpAddress(value: String): Boolean =
    Ipv4.parse(value) != null ||
        (value.contains(':') && value.length <= 45 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' })
