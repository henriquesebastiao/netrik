package com.netrik.core.portscan

/** TLS details of a service: [protocol] is null when the handshake didn't finish (certificate not trusted). */
data class TlsInfo(val protocol: String?, val subject: String?, val trusted: Boolean)

/** How a browser reaches the service. */
enum class WebScheme { Http, Https }

/**
 * What an open service said about itself: its greeting ("SSH-2.0-OpenSSH_9.6"), its HTTP answer
 * ("HTTP 200 · nginx/1.24.0") and/or its TLS certificate. Untrusted text, already cleaned.
 * [web] is set when the port answered with an HTTP status, or presented a certificate on an HTTPS port.
 */
data class ServiceBanner(val text: String?, val tls: TlsInfo? = null, val web: WebScheme? = null) {

    /** "http://192.168.0.1:8080/" to open in the browser; the default port is left out. */
    fun webUrl(ip: String, port: Int): String? {
        val scheme = web ?: return null
        val host = if (':' in ip) "[$ip]" else ip
        val default = if (scheme == WebScheme.Http) 80 else 443
        val name = if (scheme == WebScheme.Http) "http" else "https"
        return if (port == default) "$name://$host/" else "$name://$host:$port/"
    }
}

/** Pure parsing of what services send back. */
object BannerParser {

    const val MAX_LENGTH = 200

    private const val IAC = 0xFF
    private const val SB = 250
    private const val SE = 240
    private const val WILL = 251
    private const val DONT = 254

    /** Greeting of a service that speaks first, as one cleaned line; null when nothing printable came. */
    fun greeting(data: ByteArray, length: Int, port: Int): String? {
        if (length <= 0) return null
        if (port == MYSQL_PORT || looksLikeMysql(data, length)) mysql(data, length)?.let { return it }
        val text = String(stripTelnet(data, length), Charsets.ISO_8859_1)
        return clean(text)
    }

    /** "HTTP/1.1 200 OK" + Server/X-Powered-By headers → "HTTP 200 · nginx/1.24.0 · PHP/8.2". */
    fun http(response: String): String? {
        val lines = response.split("\r\n", "\n")
        val status = Regex("""^HTTP/\d(?:\.\d)?\s+(\d{3})""").find(lines.firstOrNull().orEmpty())?.groupValues?.get(1) ?: return null
        val headers = lines.drop(1).takeWhile { it.isNotBlank() }.mapNotNull { line ->
            val name = line.substringBefore(':', "").trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            if (name.isEmpty() || value.isEmpty()) null else name to value
        }.toMap()
        val parts = listOfNotNull("HTTP $status", headers["server"], headers["x-powered-by"])
        return clean(parts.joinToString(" · "))
    }

    /** Printable text only: control characters out, whitespace collapsed, first non-empty line, length limit. */
    fun clean(text: String): String? = text
        .split('\r', '\n')
        .map { line ->
            line.replace('\t', ' ')
                .filter { it >= ' ' && it != '\u007F' && it !in '\u0080'..'\u009F' }
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        .firstOrNull { it.length >= 2 }
        ?.take(MAX_LENGTH)

    /** Drops Telnet option negotiation (IAC WILL/WONT/DO/DONT x, IAC SB ... IAC SE) and keeps the text. */
    fun stripTelnet(data: ByteArray, length: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(length)
        var i = 0
        while (i < length) {
            val b = data[i].toInt() and 0xFF
            if (b != IAC) {
                out.write(b)
                i++
                continue
            }
            val command = if (i + 1 < length) data[i + 1].toInt() and 0xFF else -1
            i += when {
                command == IAC -> { out.write(IAC); 2 } // escaped 0xFF
                command in WILL..DONT -> 3
                command == SB -> {
                    var j = i + 2
                    while (j + 1 < length && !((data[j].toInt() and 0xFF) == IAC && (data[j + 1].toInt() and 0xFF) == SE)) j++
                    j + 2 - i
                }
                else -> 2
            }
        }
        return out.toByteArray()
    }

    /**
     * MySQL/MariaDB handshake: 3-byte length, sequence, then protocol 10 and the null-terminated version
     * ("8.0.36", "5.5.5-10.11.6-MariaDB"). An error packet (0xFF) carries a message, e.g. host not allowed.
     */
    fun mysql(data: ByteArray, length: Int): String? {
        if (length < 6) return null
        val payloadLength = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8) or ((data[2].toInt() and 0xFF) shl 16)
        if (payloadLength <= 0 || payloadLength > length - 4 + 64) return null
        val marker = data[4].toInt() and 0xFF
        return when (marker) {
            10 -> {
                val end = (5 until length).firstOrNull { data[it].toInt() == 0 } ?: return null
                val version = String(data, 5, end - 5, Charsets.ISO_8859_1)
                if (version.isEmpty() || version.any { it < ' ' }) return null
                if ("mariadb" in version.lowercase()) {
                    "MariaDB " + version.removePrefix("5.5.5-").substringBefore("-MariaDB")
                } else {
                    "MySQL $version"
                }
            }
            0xFF -> {
                // Error code (2 bytes), optional "#" + 5-char SQL state, then the message.
                var start = 7
                if (start < length && data[start].toInt().toChar() == '#') start += 6
                if (start >= length) return null
                clean(String(data, start, length - start, Charsets.ISO_8859_1))?.let { "MySQL: $it" }
            }
            else -> null
        }
    }

    private fun looksLikeMysql(data: ByteArray, length: Int): Boolean =
        length > 5 && data[3].toInt() == 0 && (data[4].toInt() and 0xFF) == 10

    /** "CN=router.local,O=Acme" → "router.local"; the whole DN when there's no CN. */
    fun commonName(distinguishedName: String): String? {
        val cn = Regex("""(?:^|,)\s*CN=((?:\\.|[^,])+)""", RegexOption.IGNORE_CASE).find(distinguishedName)?.groupValues?.get(1)
        return clean((cn ?: distinguishedName).replace("\\,", ","))
    }

    private const val MYSQL_PORT = 3306
}
