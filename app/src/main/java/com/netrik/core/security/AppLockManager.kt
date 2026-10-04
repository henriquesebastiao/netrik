package com.netrik.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class AppLockState(
    val enabled: Boolean = false,
    /** True while the lock screen must cover the app. */
    val locked: Boolean = false,
    val biometric: Boolean = false,
    val failures: Int = 0,
    val lockedUntilMillis: Long = 0,
)

sealed interface PinCheck {
    data object Correct : PinCheck
    /** Wrong PIN; [attemptsBeforeWait] is how many more mistakes are allowed before a wait (0 = next one waits). */
    data class Wrong(val attemptsBeforeWait: Int) : PinCheck
    /** Too many mistakes: no attempt is accepted until [untilMillis]. */
    data class Wait(val untilMillis: Long) : PinCheck
}

/**
 * App lock with a 4-digit PIN (and optionally biometrics). The app starts locked when the lock is
 * enabled, and locks again when the device screen turns off ([lock]). Wrong attempts are counted
 * persistently, with growing waits ([AppLockPolicy.waitAfter]).
 */
@Singleton
class AppLockManager @Inject constructor(
    private val store: AppLockStore,
    private val hasher: PinHasher,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private var config = AppLockConfig()
    private var loaded = false

    private val _state = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    /** Reads the stored configuration once; a cold start is locked whenever the lock is enabled. */
    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        config = store.read()
        loaded = true
        publish(locked = config.enabled)
    }

    /** Device screen turned off (or the app is otherwise asked to lock). */
    fun lock() {
        _state.update { if (it.enabled) it.copy(locked = true) else it }
    }

    suspend fun unlockWithPin(pin: String): PinCheck = mutex.withLock {
        val result = check(pin)
        if (result == PinCheck.Correct) publish(locked = false)
        result
    }

    /** Biometric success reported by the system prompt. */
    suspend fun unlockWithBiometric() = mutex.withLock {
        if (!config.enabled || !config.biometric) return@withLock
        save(config.copy(failures = 0, lockedUntilMillis = 0))
        publish(locked = false)
    }

    /** Turns the lock on with a new PIN. */
    suspend fun enable(pin: String) = mutex.withLock {
        require(AppLockPolicy.isValidPin(pin)) { "Invalid PIN" }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        save(AppLockConfig(salt = salt, verifier = hasher.hash(salt, pin), biometric = false))
        publish(locked = false)
    }

    /** Turns the lock off; needs the current PIN. */
    suspend fun disable(pin: String): PinCheck = mutex.withLock {
        val result = check(pin)
        if (result == PinCheck.Correct) {
            save(AppLockConfig())
            publish(locked = false)
        }
        result
    }

    /** Checks the current PIN before a protected change (change PIN). Counts as an attempt. */
    suspend fun confirm(pin: String): PinCheck = mutex.withLock { check(pin) }

    /** Replaces the PIN; call it only after [confirm] succeeded. Keeps the biometric choice. */
    suspend fun changePin(newPin: String) = mutex.withLock {
        require(config.enabled && AppLockPolicy.isValidPin(newPin)) { "Invalid PIN" }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        save(config.copy(salt = salt, verifier = hasher.hash(salt, newPin), failures = 0, lockedUntilMillis = 0))
        publish(locked = _state.value.locked)
    }

    suspend fun setBiometric(enabled: Boolean) = mutex.withLock {
        if (!config.enabled) return@withLock
        save(config.copy(biometric = enabled))
        publish(locked = _state.value.locked)
    }

    private suspend fun check(pin: String): PinCheck {
        val salt = config.salt ?: return PinCheck.Correct
        val now = clock.millis()
        if (now < config.lockedUntilMillis) return PinCheck.Wait(config.lockedUntilMillis)
        val matches = AppLockPolicy.isValidPin(pin) && MessageDigest.isEqual(hasher.hash(salt, pin), config.verifier)
        if (matches) {
            if (config.failures != 0 || config.lockedUntilMillis != 0L) save(config.copy(failures = 0, lockedUntilMillis = 0))
            return PinCheck.Correct
        }
        val failures = config.failures + 1
        val wait = AppLockPolicy.waitAfter(failures)
        save(config.copy(failures = failures, lockedUntilMillis = if (wait > 0) now + wait else 0))
        publish(locked = _state.value.locked)
        return if (wait > 0) PinCheck.Wait(now + wait) else PinCheck.Wrong(AppLockPolicy.FREE_ATTEMPTS - failures)
    }

    private suspend fun save(new: AppLockConfig) {
        store.write(new)
        config = new
    }

    private fun publish(locked: Boolean) {
        _state.value = AppLockState(
            enabled = config.enabled,
            locked = locked && config.enabled,
            biometric = config.biometric,
            failures = config.failures,
            lockedUntilMillis = config.lockedUntilMillis,
        )
    }

    private companion object {
        const val SALT_SIZE = 16
    }
}
