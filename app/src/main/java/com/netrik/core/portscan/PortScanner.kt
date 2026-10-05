package com.netrik.core.portscan

import com.netrik.core.common.IoDispatcher
import com.netrik.core.lan.ReachabilityProber
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface PortScanEvent {
    /** Discovery phase (Network mode): how many IPs have been probed. */
    data class Discovery(val probed: Int, val total: Int) : PortScanEvent
    data class HostUp(val ip: String) : PortScanEvent
    data class Port(val ip: String, val port: Int, val state: PortState) : PortScanEvent
    data class Progress(val checksDone: Long, val checksTotal: Long, val hostsDone: Int, val hostsTotal: Int) : PortScanEvent
    /** What an open TCP port said about itself (only when banner grabbing is on). */
    data class Banner(val ip: String, val port: Int, val banner: ServiceBanner) : PortScanEvent
}

/** Checks one port; kept separate so the scanner tests don't open sockets. */
interface PortProber {
    suspend fun tcp(ip: String, port: Int, timeoutMs: Int): PortState
    suspend fun udp(ip: String, port: Int, timeoutMs: Int): PortState
}

class SocketPortProber @Inject constructor(@param:IoDispatcher private val io: CoroutineDispatcher) : PortProber {

    /** Connected: open. Refused (RST): closed. No reply: filtered. */
    override suspend fun tcp(ip: String, port: Int, timeoutMs: Int): PortState = runInterruptible(io) {
        Socket().use { socket ->
            try {
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                PortState.Open
            } catch (e: ConnectException) {
                if (e.message?.contains("refused", ignoreCase = true) == true || e.message?.contains("ECONNREFUSED") == true) {
                    PortState.Closed
                } else {
                    PortState.Filtered
                }
            } catch (_: SocketTimeoutException) {
                PortState.Filtered
            } catch (_: IOException) {
                PortState.Filtered
            }
        }
    }

    /**
     * Reply: open. ICMP "port unreachable" (Linux delivers it to connected UDP sockets as a
     * PortUnreachableException): closed. Silence: open|filtered — they can't be told apart.
     */
    override suspend fun udp(ip: String, port: Int, timeoutMs: Int): PortState = runInterruptible(io) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(ip, port))
                val payload = UdpProbes.payloadFor(port)
                socket.send(DatagramPacket(payload, payload.size))
                val buffer = ByteArray(1500)
                socket.receive(DatagramPacket(buffer, buffer.size))
                PortState.Open
            }
        } catch (_: PortUnreachableException) {
            PortState.Closed
        } catch (_: SocketTimeoutException) {
            PortState.OpenFiltered
        } catch (_: IOException) {
            PortState.OpenFiltered
        }
    }
}

/**
 * Port scan with limited concurrency. In Network mode it first discovers the active hosts
 * (like Nmap) and only scans their ports; for a single host it scans right away.
 */
class PortScanner @Inject constructor(
    private val prober: PortProber,
    private val hostProber: ReachabilityProber,
    private val bannerGrabber: BannerGrabber,
) {

    fun scan(
        hosts: List<String>,
        ports: List<Int>,
        protocol: Protocol,
        timeoutMs: Int,
        discoverFirst: Boolean,
        grabBanners: Boolean = false,
    ): Flow<PortScanEvent> = channelFlow {
        val alive = if (discoverFirst) discover(hosts) else hosts.onEach { send(PortScanEvent.HostUp(it)) }

        val total = alive.size.toLong() * ports.size
        val done = AtomicLong()
        val hostsDone = AtomicInteger()
        val remaining = alive.associateWith { AtomicInteger(ports.size) }
        send(PortScanEvent.Progress(0, total, 0, alive.size))

        // Fixed pool of workers reading from a channel: constant memory even with 65,535 ports × 1,022 hosts.
        val workers = ScanEstimate.concurrency(protocol)
        val work = Channel<Pair<String, Int>>(capacity = workers * 2)
        // Banners run beside the scan (their own small pool), so a slow service doesn't hold a scan worker.
        val banners = Semaphore(BANNER_CONCURRENCY)
        coroutineScope {
            launch {
                for (ip in alive) for (port in ports) work.send(ip to port)
                work.close()
            }
            repeat(workers) {
                launch {
                    for ((ip, port) in work) {
                        val state = when (protocol) {
                            Protocol.Tcp -> prober.tcp(ip, port, timeoutMs)
                            Protocol.Udp -> prober.udp(ip, port, timeoutMs)
                        }
                        send(PortScanEvent.Port(ip, port, state))
                        if (grabBanners && protocol == Protocol.Tcp && state == PortState.Open) {
                            launch {
                                banners.withPermit { bannerGrabber.grab(ip, port, timeoutMs) }?.let { send(PortScanEvent.Banner(ip, port, it)) }
                            }
                        }
                        val n = done.incrementAndGet()
                        val hostFinished = remaining.getValue(ip).decrementAndGet() == 0
                        val finishedHosts = if (hostFinished) hostsDone.incrementAndGet() else hostsDone.get()
                        if (hostFinished || n % PROGRESS_EVERY == 0L || n == total) {
                            send(PortScanEvent.Progress(n, total, finishedHosts, alive.size))
                        }
                    }
                }
            }
        }
    }

    private suspend fun kotlinx.coroutines.channels.ProducerScope<PortScanEvent>.discover(hosts: List<String>): List<String> {
        val alive = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val probed = AtomicInteger()
        val semaphore = Semaphore(DISCOVERY_CONCURRENCY)
        send(PortScanEvent.Discovery(0, hosts.size))
        coroutineScope {
            hosts.forEach { ip ->
                launch {
                    semaphore.withPermit {
                        if (hostProber.probe(ip) != null) {
                            alive += ip
                            send(PortScanEvent.HostUp(ip))
                        }
                        send(PortScanEvent.Discovery(probed.incrementAndGet(), hosts.size))
                    }
                }
            }
        }
        return alive.sortedBy { ip -> ip.split('.').fold(0L) { acc, p -> (acc shl 8) or (p.toLongOrNull() ?: 0) } }
    }

    companion object {
        /** Concurrent TCP sockets: fast without exhausting descriptors or flooding the router. */
        const val TCP_CONCURRENCY = 128
        /** UDP depends on ICMP replies, which hosts rate-limit (Linux: ~1/s); more parallelism only yields false "filtered". */
        const val UDP_CONCURRENCY = 16
        const val DISCOVERY_CONCURRENCY = 32
        /** Banner grabbing holds a connection for up to a few seconds per open port. */
        const val BANNER_CONCURRENCY = 16
        private const val PROGRESS_EVERY = 16L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PortScanModule {
    @Binds
    abstract fun bindPortProber(impl: SocketPortProber): PortProber

    @Binds
    abstract fun bindPortCatalog(impl: AssetPortCatalog): PortCatalog

    @Binds
    abstract fun bindBannerGrabber(impl: SocketBannerGrabber): BannerGrabber
}
