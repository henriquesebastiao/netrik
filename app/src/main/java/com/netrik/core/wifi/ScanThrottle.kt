package com.netrik.core.wifi

/**
 * Android limit for scans requested by foreground apps: 4 every 2 minutes (Android 9+).
 * Keeps the times of the scans requested by this app to predict when the next one will be accepted.
 */
class ScanThrottle(
    private val maxScans: Int = MAX_SCANS,
    private val windowMillis: Long = WINDOW_MILLIS,
) {
    private val scans = ArrayDeque<Long>()

    fun canScan(now: Long): Boolean {
        prune(now)
        return scans.size < maxScans
    }

    /** When the next scan will be accepted (now, if it already can). */
    fun nextAllowedAt(now: Long): Long {
        prune(now)
        return if (scans.size < maxScans) now else scans.first() + windowMillis
    }

    fun record(now: Long) {
        prune(now)
        scans.addLast(now)
    }

    /** The system refused the scan: the window is full even if the local count doesn't show it (earlier scans). */
    fun markRejected(now: Long) {
        prune(now)
        while (scans.size < maxScans) scans.addFirst(now)
    }

    private fun prune(now: Long) {
        while (scans.isNotEmpty() && now - scans.first() >= windowMillis) scans.removeFirst()
    }

    companion object {
        const val MAX_SCANS = 4
        const val WINDOW_MILLIS = 120_000L
    }
}
