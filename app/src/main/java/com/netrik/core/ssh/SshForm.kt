package com.netrik.core.ssh

/** Campos do formulário "Nova conexão" que podem ter erro. */
enum class SshField { Host, Port, User, Password, Key }

/** Validação do formulário, igual à do protótipo. */
object SshForm {

    data class Input(
        val host: String,
        val port: String,
        val user: String,
        val auth: SshAuth,
        val passwordFilled: Boolean,
        val hasKey: Boolean,
        /** Ao editar, a senha em branco mantém a que já está salva. */
        val hasStoredPassword: Boolean = false,
    )

    fun validate(input: Input): Map<SshField, String> = buildMap {
        val host = input.host.trim()
        when {
            host.isEmpty() -> put(SshField.Host, "Informe o hostname ou IP")
            !isValidHost(host) -> put(SshField.Host, "Hostname ou IP inválido")
        }
        if (parsePort(input.port) == null) put(SshField.Port, "Inválida")
        when {
            input.user.isBlank() -> put(SshField.User, "Informe o usuário")
            input.user.trim().any { it.isWhitespace() || it == '@' } -> put(SshField.User, "Usuário inválido")
        }
        when (input.auth) {
            SshAuth.Password -> if (!input.passwordFilled && !input.hasStoredPassword) put(SshField.Password, "Informe a senha")
            SshAuth.Key -> if (!input.hasKey) put(SshField.Key, "Selecione um arquivo de chave")
        }
    }

    fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    /** Nome DNS, IPv4 ou IPv6 (com ou sem colchetes); sem espaços, esquema ou caminho. */
    fun isValidHost(text: String): Boolean {
        val host = text.removePrefix("[").removeSuffix("]")
        if (host.isEmpty() || host.length > 253) return false
        if (':' in host) return host.all { it.isLetterOrDigit() && it.code < 128 || it == ':' || it == '.' || it == '%' }
        return host.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '.' || it == '_' } &&
            !host.startsWith('.') && !host.startsWith('-')
    }

    /** Host sem colchetes, como o JSch espera. */
    fun normalizeHost(text: String): String = text.trim().removePrefix("[").removeSuffix("]")

    /** Nome exibido: o informado ou, em branco, o próprio host. */
    fun displayName(name: String, host: String): String = name.trim().ifEmpty { normalizeHost(host) }
}
