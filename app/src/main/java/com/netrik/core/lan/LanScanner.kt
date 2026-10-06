package com.netrik.core.lan

import android.content.Context
import android.net.wifi.WifiManager
import com.netrik.core.neighbor.Neighbor
import com.netrik.core.neighbor.NeighborDiscovery
import com.netrik.core.neighbor.NeighborEvent
import com.netrik.core.neighbor.NeighborProtocol
import com.netrik.core.network.ping.HostResolver
import com.netrik.core.oui.MacAddresses
import com.netrik.core.oui.OuiRepository
import com.netrik.core.portscan.PortCatalog
import com.netrik.core.portscan.PortProber
import com.netrik.core.portscan.PortState
import com.netrik.core.portscan.Protocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * mDNS, SSDP and MikroTik/Ubiquiti neighbor discovery. For each active host it looks up reverse DNS and NetBIOS
 * and, when asked, checks its Top 100 TCP ports plus the signature ports of [DeviceClassifier] (to tell what it is).
 * The flow ends when those checks end too. Cancelling the collection stops everything.
 */
class LanScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prober: ReachabilityProber,
    private val resolver: HostResolver,
    private val netbios: NetbiosClient,
    private val ssdp: SsdpClient,
    private val mdns: MdnsBrowser,
    private val oui: OuiRepository,
    private val neighbors: NeighborDiscovery,
    private val portProber: PortProber,
    private val portCatalog: PortCatalog,
) {

    /** [checkPorts]: quick TCP check of each host found, except the IPs in [skipPorts] (this phone). */
    fun scan(range: ScanRange, checkPorts: Boolean = false, skipPorts: Set<String> = emptySet()): Flow<ScanEvent> = channelFlow {
        // Without the MulticastLock, Wi-Fi drops multicast replies (mDNS/SSDP) to save battery.
        val lock = context.getSystemService(WifiManager::class.java)
            ?.createMulticastLock("netrik-lan")
            ?.apply { setReferenceCounted(false); acquire() }
        try {
            val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val quickPorts = if (checkPorts) (portCatalog.top(Protocol.Tcp, QUICK_TOP_PORTS) + DeviceClassifier.SIGNATURE_PORTS).distinct() else emptyList()
            // One pool for every host: the ones found first are checked first, and the sweep keeps its own sockets.
            val portPermits = Semaphore(QUICK_PORT_CONCURRENCY)

            suspend fun enrich(update: DeviceUpdate) {
                send(ScanEvent.Found(update))
                update.mac?.let { mac ->
                    val hex = mac.value.replace(":", "")
                    if (!MacAddresses.isLocallyAdministered(hex)) {
                        oui.lookup(hex)?.let { send(ScanEvent.Found(DeviceUpdate(update.ip, vendor = Sourced(it.record.organization, InfoSource.Oui)))) }
                    }
                }
            }

            /** First time an IP shows up (from any source): looks up its name via DNS and NetBIOS and checks its ports. */
            fun lookupNames(ip: String) {
                if (!seen.add(ip)) return
                if (quickPorts.isNotEmpty() && ip !in skipPorts) {
                    launch {
                        val open = coroutineScope {
                            quickPorts.map { port ->
                                async { portPermits.withPermit { port.takeIf { portProber.tcp(ip, port, QUICK_PORT_TIMEOUT_MS) == PortState.Open } } }
                            }.awaitAll().filterNotNull().toSet()
                        }
                        send(ScanEvent.Found(DeviceUpdate(ip, openPorts = open)))
                    }
                }
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
                launch {
                    neighbors.discover().collect { event ->
                        val update = (event as? NeighborEvent.Found)?.neighbor?.toDeviceUpdate() ?: return@collect
                        if (update.ip in inRange) { enrich(update); lookupNames(update.ip) }
                    }
                }
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
        const val QUICK_TOP_PORTS = 100
        /** Sockets for the quick port check, shared by all hosts. */
        const val QUICK_PORT_CONCURRENCY = 64
        /** On the LAN a closed port answers in milliseconds; only filtered ones wait this long. */
        const val QUICK_PORT_TIMEOUT_MS = 800
    }
}

/** What a MikroTik/Ubiquiti announcement tells about the host: the name the admin gave it, its MAC and model. */
internal fun Neighbor.toDeviceUpdate(): DeviceUpdate {
    val source = if (protocol == NeighborProtocol.Mndp) InfoSource.Mndp else InfoSource.Ubiquiti
    return DeviceUpdate(
        ip = address,
        detection = if (protocol == NeighborProtocol.Mndp) Detection.Mndp else Detection.Ubiquiti,
        hostname = identity?.let { Sourced(it, source) },
        mac = mac?.let { Sourced(it, source) },
        model = model,
    )
}
