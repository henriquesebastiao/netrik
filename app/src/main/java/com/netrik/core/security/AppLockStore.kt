package com.netrik.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Persisted app lock configuration. The PIN itself is never stored, only its keyed verifier. */
data class AppLockConfig(
    val salt: ByteArray? = null,
    val verifier: ByteArray? = null,
    val biometric: Boolean = false,
    /** Consecutive wrong attempts; kept across restarts so killing the app doesn't reset the wait. */
    val failures: Int = 0,
    val lockedUntilMillis: Long = 0,
) {
    val enabled: Boolean get() = salt != null && verifier != null
}

/** Storage of [AppLockConfig]; an interface so the lock logic can be tested without Android. */
interface AppLockStore {
    suspend fun read(): AppLockConfig
    suspend fun write(config: AppLockConfig)
}

private val Context.appLockStore: DataStore<Preferences> by preferencesDataStore(name = "app_lock")

@Singleton
class DataStoreAppLockStore @Inject constructor(@param:ApplicationContext private val context: Context) : AppLockStore {

    override suspend fun read(): AppLockConfig {
        val prefs = context.appLockStore.data.first()
        return AppLockConfig(
            salt = prefs[SALT]?.let(::decode),
            verifier = prefs[VERIFIER]?.let(::decode),
            biometric = prefs[BIOMETRIC] ?: false,
            failures = prefs[FAILURES] ?: 0,
            lockedUntilMillis = prefs[LOCKED_UNTIL] ?: 0,
        )
    }

    override suspend fun write(config: AppLockConfig) {
        context.appLockStore.edit { prefs ->
            if (config.salt != null && config.verifier != null) {
                prefs[SALT] = encode(config.salt)
                prefs[VERIFIER] = encode(config.verifier)
            } else {
                prefs.remove(SALT)
                prefs.remove(VERIFIER)
            }
            prefs[BIOMETRIC] = config.biometric
            prefs[FAILURES] = config.failures
            prefs[LOCKED_UNTIL] = config.lockedUntilMillis
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String) = Base64.getDecoder().decode(text)

    private companion object {
        val SALT = stringPreferencesKey("salt")
        val VERIFIER = stringPreferencesKey("verifier")
        val BIOMETRIC = booleanPreferencesKey("biometric")
        val FAILURES = intPreferencesKey("failures")
        val LOCKED_UNTIL = longPreferencesKey("locked_until")
    }
}
