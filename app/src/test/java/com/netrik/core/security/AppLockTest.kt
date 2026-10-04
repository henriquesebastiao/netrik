package com.netrik.core.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AppLockTest {

    /** Same construction as the Keystore hasher, with a key kept in the test. */
    private class TestHasher : PinHasher {
        private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "HmacSHA256")
        override fun hash(salt: ByteArray, pin: String): ByteArray =
            Mac.getInstance("HmacSHA256").run {
                init(key)
                update(salt)
                doFinal(pin.toByteArray())
            }
    }

    private class MemoryStore : AppLockStore {
        var saved = AppLockConfig()
        override suspend fun read() = saved
        override suspend fun write(config: AppLockConfig) {
            saved = config
        }
    }

    private class MutableClock(var now: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }

    private fun manager(store: MemoryStore = MemoryStore(), clock: Clock = MutableClock(1_000_000)) =
        AppLockManager(store, TestHasher(), clock)

    @Test
    fun `PIN must have exactly 4 digits`() {
        assertTrue(AppLockPolicy.isValidPin("0123"))
        assertFalse(AppLockPolicy.isValidPin("123"))
        assertFalse(AppLockPolicy.isValidPin("12345"))
        assertFalse(AppLockPolicy.isValidPin("12a4"))
        assertFalse(AppLockPolicy.isValidPin("１２３４")) // full-width digits
    }

    @Test
    fun `wait grows after the free attempts and is capped`() {
        assertEquals(0, AppLockPolicy.waitAfter(4))
        assertEquals(30_000, AppLockPolicy.waitAfter(5))
        assertEquals(60_000, AppLockPolicy.waitAfter(6))
        assertEquals(120_000, AppLockPolicy.waitAfter(7))
        assertEquals(15 * 60_000L, AppLockPolicy.waitAfter(50))
    }

    @Test
    fun `the PIN is never stored, only a salted verifier`() = runTest {
        val store = MemoryStore()
        manager(store).enable("4821")
        val saved = store.saved
        assertTrue(saved.enabled)
        assertFalse(String(saved.verifier!!).contains("4821"))
        // Same PIN on another enable gets a new salt and a different verifier.
        val other = MemoryStore()
        manager(other).enable("4821")
        assertNotEquals(saved.verifier!!.toList(), other.saved.verifier!!.toList())
    }

    @Test
    fun `cold start is locked and the right PIN unlocks`() = runTest {
        val store = MemoryStore()
        manager(store).enable("4821")
        val restarted = manager(store)
        restarted.load()
        assertTrue(restarted.state.value.locked)
        assertEquals(PinCheck.Correct, restarted.unlockWithPin("4821"))
        assertFalse(restarted.state.value.locked)
    }

    @Test
    fun `screen off locks only when the lock is on`() = runTest {
        val off = manager()
        off.load()
        off.lock()
        assertFalse(off.state.value.locked)

        val on = manager()
        on.load()
        on.enable("4821")
        on.lock()
        assertTrue(on.state.value.locked)
    }

    @Test
    fun `wrong attempts lead to a wait that survives a restart`() = runTest {
        val store = MemoryStore()
        val clock = MutableClock(1_000_000)
        val lock = manager(store, clock)
        lock.enable("4821")
        repeat(4) { i -> assertEquals(PinCheck.Wrong(4 - i), lock.unlockWithPin("0000")) }
        assertEquals(PinCheck.Wait(1_000_000 + 30_000), lock.unlockWithPin("0000"))

        // Killing the app doesn't reset the wait, and not even the right PIN is checked meanwhile.
        val restarted = manager(store, clock)
        restarted.load()
        assertEquals(PinCheck.Wait(1_030_000), restarted.unlockWithPin("4821"))

        clock.now += 30_000
        assertEquals(PinCheck.Correct, restarted.unlockWithPin("4821"))
        assertEquals(0, store.saved.failures)
    }

    @Test
    fun `turning off and changing the PIN need the current one`() = runTest {
        val store = MemoryStore()
        val lock = manager(store)
        lock.enable("4821")
        assertEquals(PinCheck.Wrong(4), lock.disable("1111"))
        assertTrue(store.saved.enabled)

        assertEquals(PinCheck.Correct, lock.confirm("4821"))
        lock.changePin("9090")
        lock.lock()
        assertEquals(PinCheck.Wrong(4), lock.unlockWithPin("4821"))
        assertEquals(PinCheck.Correct, lock.unlockWithPin("9090"))

        assertEquals(PinCheck.Correct, lock.disable("9090"))
        assertFalse(store.saved.enabled)
        assertFalse(lock.state.value.enabled)
    }

    @Test
    fun `biometric unlock only works when it was turned on`() = runTest {
        val lock = manager()
        lock.enable("4821")
        lock.lock()
        lock.unlockWithBiometric()
        assertTrue(lock.state.value.locked)

        lock.setBiometric(true)
        lock.unlockWithBiometric()
        assertFalse(lock.state.value.locked)
    }
}
