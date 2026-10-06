package com.netrik.core.portscan

import android.annotation.SuppressLint
import com.netrik.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Asks an open TCP port what it is; null when it says nothing useful. Kept apart so the scanner tests don't open sockets. */
fun interface BannerGrabber {
    suspend fun grab(ip: String, port: Int, timeoutMs: Int): ServiceBanner?
}

/**
 * Banner grabbing with plain sockets:
 * - services that speak first (SSH, FTP, SMTP, POP3, IMAP, VNC, Telnet, MySQL) are only read;
 * - silent ones get `HEAD / HTTP/1.0`, and the status line and Server header are kept;
 * - TLS ports go through a normal handshake. The certificate is validated as usual (no "trust everything"):
 *   its name is read even when it isn't trusted, but the HTTP request only goes over a trusted connection.
 */
class SocketBannerGrabber @Inject constructor(
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BannerGrabber {

    override suspend fun grab(ip: String, port: Int, timeoutMs: Int): ServiceBanner? = runInterruptible(io) {
        try {
            if (port in TLS_PORTS) tls(ip, port, timeoutMs) else plain(ip, port, timeoutMs)
        } catch (_: IOException) {
            null
        }
    }

    private fun plain(ip: String, port: Int, timeoutMs: Int): ServiceBanner? = connect(ip, port, timeoutMs).use { socket ->
        val input = socket.getInputStream()
        // Known web ports are asked right away; the others get a moment to greet first.
        if (port !in HTTP_PORTS) {
            socket.soTimeout = GREETING_WAIT_MS
            val buffer = ByteArray(MAX_READ)
            val length = readSome(input, buffer)
            if (length > 0) return@use BannerParser.greeting(buffer, length, port)?.let { ServiceBanner(it) }
        }
        socket.soTimeout = RESPONSE_WAIT_MS
        httpProbe(socket, ip)?.let { (text, http) -> ServiceBanner(text, web = if (http) WebScheme.Http else null) }
    }

    private fun tls(ip: String, port: Int, timeoutMs: Int): ServiceBanner? {
        val plain = connect(ip, port, timeoutMs)
        val recorder = RecordingTrustManager(systemTrustManager())
        val socket = try {
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(recorder), null) }
            context.socketFactory.createSocket(plain, ip, port, true) as SSLSocket
        } catch (e: Exception) {
            plain.close()
            throw IOException(e)
        }
        return socket.use {
            it.soTimeout = RESPONSE_WAIT_MS
            val subject = { recorder.chain?.firstOrNull()?.subjectX500Principal?.name?.let(BannerParser::commonName) }
            try {
                it.startHandshake()
            } catch (_: SSLException) {
                // Untrusted (self-signed, expired...) or not TLS at all: report the certificate when there was one.
                // No HTTP goes over it, but on an HTTPS port the browser can still open it (and show its own warning).
                return@use recorder.chain?.let {
                    ServiceBanner(null, TlsInfo(null, subject(), trusted = false), web = if (port in HTTPS_PORTS) WebScheme.Https else null)
                }
            }
            val info = TlsInfo(it.session.protocol, subject(), trusted = true)
            if (port in HTTPS_PORTS) {
                val answer = httpProbe(it, ip)
                ServiceBanner(answer?.first, info, web = if (answer?.second == true) WebScheme.Https else null)
            } else {
                val buffer = ByteArray(MAX_READ)
                ServiceBanner(readSome(it.inputStream, buffer).takeIf { n -> n > 0 }?.let { n -> BannerParser.greeting(buffer, n, port) }, info)
            }
        }
    }

    private fun connect(ip: String, port: Int, timeoutMs: Int): Socket = Socket().apply {
        try {
            connect(InetSocketAddress(ip, port), (timeoutMs * 2).coerceIn(CONNECT_MIN_MS, CONNECT_MAX_MS))
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    /** The answer as one line, and whether it was an HTTP status line. */
    private fun httpProbe(socket: Socket, ip: String): Pair<String?, Boolean>? {
        val request = "HEAD / HTTP/1.0\r\nHost: $ip\r\nUser-Agent: Netrik\r\nAccept: */*\r\n\r\n"
        socket.getOutputStream().apply {
            write(request.toByteArray(Charsets.US_ASCII))
            flush()
        }
        val buffer = ByteArray(MAX_READ)
        val length = readSome(socket.getInputStream(), buffer)
        if (length <= 0) return null
        val response = String(buffer, 0, length, Charsets.ISO_8859_1)
        BannerParser.http(response)?.let { return it to true }
        return BannerParser.greeting(buffer, length, 0)?.let { it to false }
    }

    /** One read (what arrived first); 0 on timeout or end of stream. */
    private fun readSome(input: InputStream, buffer: ByteArray): Int = try {
        input.read(buffer).coerceAtLeast(0)
    } catch (_: SocketTimeoutException) {
        0
    }

    private fun systemTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * Validates exactly like the system trust manager (rejecting what it rejects) and only remembers the chain
     * the server presented, so its name can be shown even when the handshake fails.
     */
    @SuppressLint("CustomX509TrustManager")
    private class RecordingTrustManager(private val delegate: X509TrustManager) : X509TrustManager {
        @Volatile var chain: Array<X509Certificate>? = null

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            this.chain = chain
            delegate.checkServerTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            throw CertificateException("Client certificates are not accepted")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    }

    companion object {
        /** HTTPS and TLS-wrapped mail/directory ports. */
        val TLS_PORTS = setOf(443, 465, 636, 853, 993, 995, 8443, 9443)
        val HTTPS_PORTS = setOf(443, 8443, 9443)
        /** Usual web ports: ask with HTTP straight away instead of waiting for a greeting. */
        val HTTP_PORTS = setOf(80, 81, 591, 3000, 5000, 5001, 7080, 8000, 8008, 8080, 8081, 8088, 8888, 9000, 9090)
        private const val GREETING_WAIT_MS = 1_500
        private const val RESPONSE_WAIT_MS = 2_000
        private const val CONNECT_MIN_MS = 1_000
        private const val CONNECT_MAX_MS = 3_000
        private const val MAX_READ = 2_048
    }
}
