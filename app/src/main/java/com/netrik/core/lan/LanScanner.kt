package com.netrik.core.lan

import android.content.Context
import android.net.wifi.WifiManager
import com.netrik.core.network.ping.HostResolver
import com.netrik.core.oui.MacAddresses
import com.netrik.core.oui.OuiRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

sealed interface ScanEvent {
    data class Progress(val scanned: Int, val total: Int) : ScanEvent
    data class Found(val update: DeviceUpdate) : ScanEvent
    /** The IP sweep ended; the name sources may still fill in data for a few seconds. */
    data object SweepDone : ScanEvent
}

/**
 * Discovers devices on the subnet: concurrent probing (ping + TCP) of each IP and, in parallel,
 * mDNS and SSDP. For each active host it looks up reverse DNS and NetBIOS. Cancelling the collection stops everything.
 */
class LanScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prober: ReachabilityProber,
    private val resolver: HostResolver,
    private val netbios: NetbiosClient,
    private val ssdp: SsdpClient,
    private val mdns: MdnsBrowser,
    private val oui: OuiRepository,
) {

    fun scan(range: ScanRange): Flow<ScanEvent> = channelFlow {
        // Without the MulticastLock, Wi-Fi drops multicast replies (mDNS/SSDP) to save battery.
        val lock = context.getSystemService(WifiManager::class.java)
            ?.createMulticastLock("netrik-lan")
            ?.apply { setReferenceCounted(false); acquire() }
        try {
            val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            suspend fun enrich(update: DeviceUpdate) {
                send(ScanEvent.Found(update))
                update.mac?.let { mac ->
                    val hex = mac.value.replace(":", "")
                    if (!MacAddresses.isLocallyAdministered(hex)) {
                        oui.lookup(hex)?.let { send(ScanEvent.Found(DeviceUpdate(update.ip, vendor = Sourced(it.record.organization, InfoSource.Oui)))) }
                    }
                }
            }

            /** First time an IP shows up (from any source): looks up its name via DNS and NetBIOS. */
            fun lookupNames(ip: String) {
                if (!seen.add(ip)) return
                launch { resolver.reverse(ip)?.let { send(ScanEvent.Found(DeviceUpdate(ip, hostname = Sourced(it, InfoSource.Dns)))) } }
                launch {
                    netbios.query(ip)?.let { status ->
                        enrich(
                            DeviceUpdate(
                                ip = ip,
                                hostname = status.computerName?.let { Sourced(it, InfoSource.Netbios) },
                                mac = status.mac?.let { Sourced(it, InfoSource.Netbios) },
                            ),
                        )
                    }
                }
            }

            val inRange = range.hosts.toHashSet()
            val discovery = launch {
                launch { mdns.browse().collect { if (it.ip in inRange) { enrich(it); lookupNames(it.ip) } } }
                launch { ssdp.discover().collect { if (it.ip in inRange) { enrich(it); lookupNames(it.ip) } } }
            }

            val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
            val scanned = AtomicInteger()
            val total = range.hosts.size
            send(ScanEvent.Progress(0, total))
            coroutineScope {
                range.hosts.forEach { ip ->
                    launch {
                        semaphore.withPermit {
                            prober.probe(ip)?.let { result ->
                                send(ScanEvent.Found(DeviceUpdate(ip, detection = result.detection, rttMs = result.rttMs)))
                                lookupNames(ip)
                            }
                            send(ScanEvent.Progress(scanned.incrementAndGet(), total))
                        }
                    }
                }
            }
            send(ScanEvent.SweepDone)
            // Gives time to the mDNS, NetBIOS and UPnP replies still arriving.
            delay(LINGER_MILLIS)
            discovery.cancel()
        } finally {
            lock?.release()
        }
    }

    companion object {
        /** Each probe opens 1 ping process and 8 TCP sockets; 32 hosts at a time keeps it light. */
        const val MAX_CONCURRENT_PROBES = 32
        const val LINGER_MILLIS = 3_000L
    }
}
