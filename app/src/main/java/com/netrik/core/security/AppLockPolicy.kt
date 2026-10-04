package com.netrik.core.security

/** Pure rules of the app lock: PIN format and the wait after wrong attempts. */
object AppLockPolicy {
    const val PIN_LENGTH = 4

    /** Wrong attempts allowed before the first wait. */
    const val FREE_ATTEMPTS = 5

    private const val FIRST_WAIT_MS = 30_000L
    private const val MAX_WAIT_MS = 15 * 60_000L

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    /**
     * Wait after [failures] consecutive wrong attempts: none for the first [FREE_ATTEMPTS], then
     * 30 s doubling on every further mistake, up to 15 min.
     */
    fun waitAfter(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (FIRST_WAIT_MS shl doublings).coerceAtMost(MAX_WAIT_MS)
    }
}
