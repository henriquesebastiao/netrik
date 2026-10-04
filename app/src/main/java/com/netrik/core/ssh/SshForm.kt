package com.netrik.core.ssh

/** Fields of the "New connection" form that can show an error. */
enum class SshField { Host, Port, User, Password, Key }

/** Validation errors; the UI turns them into translated text. */
enum class SshFieldError { HostRequired, HostInvalid, PortInvalid, UserRequired, UserInvalid, PasswordRequired, KeyRequired, PasswordRejected }

/** Form validation, same as the prototype. */
object SshForm {

    data class Input(
        val host: String,
        val port: String,
        val user: String,
        val auth: SshAuth,
        val passwordFilled: Boolean,
        val hasKey: Boolean,
        /** When editing, a blank password keeps the one already saved. */
        val hasStoredPassword: Boolean = false,
    )

    fun validate(input: Input): Map<SshField, SshFieldError> = buildMap {
        val host = input.host.trim()
        when {
            host.isEmpty() -> put(SshField.Host, SshFieldError.HostRequired)
            !isValidHost(host) -> put(SshField.Host, SshFieldError.HostInvalid)
        }
        if (parsePort(input.port) == null) put(SshField.Port, SshFieldError.PortInvalid)
        when {
            input.user.isBlank() -> put(SshField.User, SshFieldError.UserRequired)
            input.user.trim().any { it.isWhitespace() || it == '@' } -> put(SshField.User, SshFieldError.UserInvalid)
        }
        when (input.auth) {
            SshAuth.Password -> if (!input.passwordFilled && !input.hasStoredPassword) put(SshField.Password, SshFieldError.PasswordRequired)
            SshAuth.Key -> if (!input.hasKey) put(SshField.Key, SshFieldError.KeyRequired)
        }
    }

    fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    /** DNS name, IPv4 or IPv6 (with or without brackets); no spaces, scheme or path. */
    fun isValidHost(text: String): Boolean {
        val host = text.removePrefix("[").removeSuffix("]")
        if (host.isEmpty() || host.length > 253) return false
        if (':' in host) return host.all { it.isLetterOrDigit() && it.code < 128 || it == ':' || it == '.' || it == '%' }
        return host.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '.' || it == '_' } &&
            !host.startsWith('.') && !host.startsWith('-')
    }

    /** Host without brackets, as JSch expects. */
    fun normalizeHost(text: String): String = text.trim().removePrefix("[").removeSuffix("]")

    /** Display name: the one given or, if blank, the host itself. */
    fun displayName(name: String, host: String): String = name.trim().ifEmpty { normalizeHost(host) }
}
