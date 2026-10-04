package com.netrik.core.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair

/** Configuração global do JSch para o Android. */
object JschSetup {
    @Volatile private var installed = false

    /**
     * O JSch escolhe entre as classes JCE e BouncyCastle conforme a versão do Java; no Android
     * a escolha cai nas JCE, que pedem algoritmos (Ed25519, X25519, ML-KEM) que o provedor do
     * sistema não oferece em todas as versões suportadas. Forçamos as implementações BouncyCastle.
     */
    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
            JSch.setConfig("ssh-ed448", "com.jcraft.jsch.bc.SignatureEd448")
            JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
            JSch.setConfig("keypairgen_fromprivate.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
            JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH")
            JSch.setConfig("mlkem768", "com.jcraft.jsch.bc.MLKEM768")
            JSch.setConfig("mlkem1024", "com.jcraft.jsch.bc.MLKEM1024")
            installed = true
        }
    }
}

/** Resultado da leitura de um arquivo de chave privada. */
sealed interface KeyInspection {
    /** [summary]: "ED25519", "RSA 4096", "ECDSA P-256". */
    data class Valid(val summary: String, val encrypted: Boolean) : KeyInspection
    /** A chave é cifrada e a senha informada não a abre. */
    data object WrongPassphrase : KeyInspection
    /** Não é uma chave privada que o app saiba ler (ou é só a chave pública). */
    data object Invalid : KeyInspection
    data object TooLarge : KeyInspection
}

object PrivateKeys {
    /** Chaves privadas reais têm poucos KB; acima disso é outro tipo de arquivo. */
    const val MAX_SIZE = 64 * 1024

    /**
     * Lê a chave (OpenSSH, PEM/PKCS#8 ou PuTTY) sem guardar nada. Com [passphrase], confere se ela
     * abre a chave cifrada; sem ela, só identifica o tipo.
     */
    fun inspect(bytes: ByteArray, passphrase: ByteArray? = null): KeyInspection {
        if (bytes.size > MAX_SIZE) return KeyInspection.TooLarge
        JschSetup.install()
        val pair = try {
            KeyPair.load(JSch(), bytes.copyOf(), null)
        } catch (_: JSchException) {
            return KeyInspection.Invalid
        } catch (_: RuntimeException) {
            return KeyInspection.Invalid
        }
        try {
            val type = pair.keyType
            if (type == KeyPair.ERROR || type == KeyPair.UNKNOWN || type == KeyPair.DEFERRED && !pair.isEncrypted) {
                return KeyInspection.Invalid
            }
            val encrypted = pair.isEncrypted
            if (encrypted && passphrase != null && passphrase.isNotEmpty() && !pair.decrypt(passphrase.copyOf())) {
                return KeyInspection.WrongPassphrase
            }
            return KeyInspection.Valid(summary(pair), encrypted)
        } finally {
            pair.dispose()
        }
    }

    private fun summary(pair: KeyPair): String = when (pair.keyType) {
        KeyPair.ED25519 -> "ED25519"
        KeyPair.ED448 -> "ED448"
        KeyPair.RSA -> pair.keySize.takeIf { it > 0 }?.let { "RSA $it" } ?: "RSA"
        KeyPair.ECDSA -> pair.keySize.takeIf { it > 0 }?.let { "ECDSA P-$it" } ?: "ECDSA"
        KeyPair.DSA -> "DSA"
        // Chave PuTTY/OpenSSH cifrada: o tipo só aparece depois de decifrar.
        else -> pair.keyTypeString?.let(HostKeys::displayType) ?: "Chave"
    }

    /** "411 bytes", "3,2 KB". */
    fun formatSize(bytes: Int): String =
        if (bytes < 1024) "$bytes bytes" else String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f KB", bytes / 1024.0)
}
