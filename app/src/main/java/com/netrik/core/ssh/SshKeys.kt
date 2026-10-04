package com.netrik.core.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair

/** Global JSch configuration for Android. */
object JschSetup {
    @Volatile private var installed = false

    /**
     * JSch picks between the JCE and BouncyCastle classes depending on the Java version; on Android
     * it lands on JCE, which needs algorithms (Ed25519, X25519, ML-KEM) the system provider
     * doesn't offer on every supported version. We force the BouncyCastle implementations.
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

/** Result of reading a private key file. */
sealed interface KeyInspection {
    /** [summary]: "ED25519", "RSA 4096", "ECDSA P-256". */
    data class Valid(val summary: String, val encrypted: Boolean) : KeyInspection
    /** The key is encrypted and the given passphrase doesn't open it. */
    data object WrongPassphrase : KeyInspection
    /** Not a private key the app can read (or only the public key). */
    data object Invalid : KeyInspection
    data object TooLarge : KeyInspection
}

object PrivateKeys {
    /** Real private keys are a few KB; above this it's some other kind of file. */
    const val MAX_SIZE = 64 * 1024

    /**
     * Reads the key (OpenSSH, PEM/PKCS#8 or PuTTY) without storing anything. With [passphrase], checks that
     * it opens the encrypted key; without it, only identifies the type.
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
        // Encrypted PuTTY/OpenSSH key: the type only shows up after decrypting.
        else -> pair.keyTypeString?.let(HostKeys::displayType) ?: "Key"
    }

    /** "411 bytes", "3.2 KB" ("3,2 KB" in Portuguese). */
    fun formatSize(bytes: Int, locale: java.util.Locale = java.util.Locale.getDefault()): String =
        if (bytes < 1024) "$bytes bytes" else String.format(locale, "%.1f KB", bytes / 1024.0)
}
