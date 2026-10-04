package com.netrik.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/** Turns the PIN into a verifier that can't be brute-forced away from this device. */
fun interface PinHasher {
    fun hash(salt: ByteArray, pin: String): ByteArray
}

/**
 * HMAC-SHA256 with a non-exportable key kept in the Android Keystore. A 4-digit PIN has only 10,000
 * values, so a plain hash would be cracked instantly from a copy of the app data; with the key
 * locked in the Keystore, every guess has to go through this device (and its attempt limit).
 */
@Singleton
class KeystorePinHasher @Inject constructor() : PinHasher {

    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun hash(salt: ByteArray, pin: String): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key)
        mac.update(salt)
        return mac.doFinal(pin.toByteArray(Charsets.UTF_8))
    }

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "netrik_app_lock_pin"
        const val ALGORITHM = "HmacSHA256"
    }
}
