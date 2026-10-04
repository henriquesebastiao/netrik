package com.netrik.core.wifi

/**
 * Limite do Android para scans pedidos por apps em primeiro plano: 4 a cada 2 minutos (Android 9+).
 * Guarda os horários dos scans pedidos por este app para prever quando o próximo será aceito.
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

    /** Quando o próximo scan será aceito (agora, se já puder). */
    fun nextAllowedAt(now: Long): Long {
        prune(now)
        return if (scans.size < maxScans) now else scans.first() + windowMillis
    }

    fun record(now: Long) {
        prune(now)
        scans.addLast(now)
    }

    /** O sistema recusou o scan: a janela está cheia mesmo que a conta local não mostre (scans de antes). */
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
