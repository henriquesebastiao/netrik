package com.netrik.core.ssh

enum class SshAuth(val storedName: String) {
    Password("password"),
    Key("key"),
    ;

    companion object {
        fun fromStored(value: String): SshAuth = entries.firstOrNull { it.storedName == value } ?: Password
    }
}

/** Host salvo, sem os segredos (eles só são decifrados na hora de conectar). */
data class SshHost(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val auth: SshAuth,
    val groupId: Long?,
    val keyName: String?,
    val keyInfo: String?,
) {
    /** "user@host:porta", como na lista do design. */
    val address: String get() = "$username@$host:$port"
}

data class SshGroup(val id: Long, val name: String, val expanded: Boolean)

/** Arquivo de chave privada escolhido no formulário. */
class PrivateKeyFile(val name: String, val bytes: ByteArray, val summary: String, val encrypted: Boolean) {
    /** "ED25519 · 411 bytes". */
    val info: String get() = "$summary · ${PrivateKeys.formatSize(bytes.size)}"
}

/** Dados de uma conexão, com os segredos em claro só durante a tentativa. */
class SshTarget(
    val host: String,
    val port: Int,
    val username: String,
    val auth: SshAuth,
    val password: ByteArray? = null,
    val privateKey: ByteArray? = null,
    val keyPassphrase: ByteArray? = null,
) {
    val hostId: String get() = HostKeys.hostId(host, port)

    /** Apaga os segredos da memória depois do uso. */
    fun wipe() {
        password?.fill(0)
        privateKey?.fill(0)
        keyPassphrase?.fill(0)
    }

    override fun toString(): String = "SshTarget($username@$host:$port, $auth)"
}
