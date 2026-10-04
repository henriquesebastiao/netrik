package com.netrik.core.ssh

import java.security.MessageDigest
import java.util.Base64

/** Chave pública apresentada por um servidor SSH (formato de transmissão do RFC 4253). */
data class HostKey(val type: String, val blob: ByteArray) {
    /** Impressão digital no formato do OpenSSH: "SHA256:" + Base64 sem "=". */
    val fingerprint: String get() = HostKeys.fingerprint(blob)
    val base64: String get() = Base64.getEncoder().encodeToString(blob)

    override fun equals(other: Any?): Boolean = other is HostKey && type == other.type && blob.contentEquals(other.blob)
    override fun hashCode(): Int = 31 * type.hashCode() + blob.contentHashCode()
}

enum class HostKeyStatus { Known, Unknown, Changed }

object HostKeys {

    fun fingerprint(blob: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))

    /** O blob começa com o nome do algoritmo como string SSH (uint32 big-endian + bytes). */
    fun typeOf(blob: ByteArray): String? {
        if (blob.size < 4) return null
        val len = ((blob[0].toInt() and 0xFF) shl 24) or ((blob[1].toInt() and 0xFF) shl 16) or
            ((blob[2].toInt() and 0xFF) shl 8) or (blob[3].toInt() and 0xFF)
        if (len <= 0 || len > 64 || 4 + len > blob.size) return null
        return String(blob, 4, len, Charsets.US_ASCII)
    }

    /** Nome curto para a interface: "ssh-ed25519" → "ED25519", "ecdsa-sha2-nistp256" → "ECDSA P-256". */
    fun displayType(type: String): String = when {
        type == "ssh-ed25519" -> "ED25519"
        type == "ssh-ed448" -> "ED448"
        type == "ssh-rsa" || type.startsWith("rsa-sha2") -> "RSA"
        type.startsWith("ecdsa-sha2-nistp") -> "ECDSA P-" + type.removePrefix("ecdsa-sha2-nistp")
        type == "ssh-dss" -> "DSA"
        else -> type.uppercase()
    }

    /** Compara a chave apresentada com a salva para o mesmo host:porta. */
    fun status(stored: HostKey?, presented: HostKey): HostKeyStatus = when {
        stored == null -> HostKeyStatus.Unknown
        stored == presented -> HostKeyStatus.Known
        else -> HostKeyStatus.Changed
    }

    /** Identificador do host no known_hosts: "host" na porta 22, "[host]:porta" nas demais (como o OpenSSH). */
    fun hostId(host: String, port: Int): String = if (port == 22) host.lowercase() else "[${host.lowercase()}]:$port"
}
