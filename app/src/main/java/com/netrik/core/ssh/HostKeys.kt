package com.netrik.core.ssh

import java.security.MessageDigest
import java.util.Base64

/** Public key presented by an SSH server (RFC 4253 wire format). */
data class HostKey(val type: String, val blob: ByteArray) {
    /** Fingerprint in OpenSSH format: "SHA256:" + Base64 without "=". */
    val fingerprint: String get() = HostKeys.fingerprint(blob)
    val base64: String get() = Base64.getEncoder().encodeToString(blob)

    override fun equals(other: Any?): Boolean = other is HostKey && type == other.type && blob.contentEquals(other.blob)
    override fun hashCode(): Int = 31 * type.hashCode() + blob.contentHashCode()
}

enum class HostKeyStatus { Known, Unknown, Changed }

object HostKeys {

    fun fingerprint(blob: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))

    /** The blob starts with the algorithm name as an SSH string (big-endian uint32 + bytes). */
    fun typeOf(blob: ByteArray): String? {
        if (blob.size < 4) return null
        val len = ((blob[0].toInt() and 0xFF) shl 24) or ((blob[1].toInt() and 0xFF) shl 16) or
            ((blob[2].toInt() and 0xFF) shl 8) or (blob[3].toInt() and 0xFF)
        if (len <= 0 || len > 64 || 4 + len > blob.size) return null
        return String(blob, 4, len, Charsets.US_ASCII)
    }

    /** Short name for the UI: "ssh-ed25519" → "ED25519", "ecdsa-sha2-nistp256" → "ECDSA P-256". */
    fun displayType(type: String): String = when {
        type == "ssh-ed25519" -> "ED25519"
        type == "ssh-ed448" -> "ED448"
        type == "ssh-rsa" || type.startsWith("rsa-sha2") -> "RSA"
        type.startsWith("ecdsa-sha2-nistp") -> "ECDSA P-" + type.removePrefix("ecdsa-sha2-nistp")
        type == "ssh-dss" -> "DSA"
        else -> type.uppercase()
    }

    /** Compares the presented key with the one saved for the same host:port. */
    fun status(stored: HostKey?, presented: HostKey): HostKeyStatus = when {
        stored == null -> HostKeyStatus.Unknown
        stored == presented -> HostKeyStatus.Known
        else -> HostKeyStatus.Changed
    }

    /** Host id in known_hosts: "host" on port 22, "[host]:port" otherwise (like OpenSSH). */
    fun hostId(host: String, port: Int): String = if (port == 22) host.lowercase() else "[${host.lowercase()}]:$port"
}
