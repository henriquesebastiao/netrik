package com.netrik.core.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.netrik.core.common.IoDispatcher
import com.netrik.core.network.ping.PingCommand
import com.netrik.core.network.ping.PingEvent
import com.netrik.core.network.ping.PingRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import javax.inject.Inject
import kotlin.coroutines.resume

/** Verifica se um IP está ativo: ping ou conexão TCP aceita/recusada (recusa também prova o host). */
class ReachabilityProber @Inject constructor(
    private val ping: PingRunner,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    data class Result(val detection: Detection, val rttMs: Double?)

    suspend fun probe(ip: String): Result? = coroutineScope {
        val icmp = async {
            ping.run(PingCommand.probe(ip, ipv6 = false, ttl = null, timeoutSeconds = 1))
                .firstOrNull { it is PingEvent.Reply || it is PingEvent.Exited }
                ?.let { it as? PingEvent.Reply }
                ?.let { Result(Detection.Icmp, it.timeMs) }
        }
        val tcp = PROBE_PORTS.map { port -> async { if (tcpAnswers(ip, port)) Result(Detection.Tcp(port), null) else null } }
        val all = listOf(icmp) + tcp
        // O primeiro positivo vence; cancela as demais sondas.
        var pending = all
        var found: Result? = null
        while (pending.isNotEmpty() && found == null) {
            val (done, value) = select { pending.forEach { d -> d.onAwait { d to it } } }
            pending = pending - done
            found = value
        }
        pending.forEach { it.cancel() }
        found
    }

    private suspend fun tcpAnswers(ip: String, port: Int): Boolean = runInterruptible(io) {
        Socket().use { socket ->
            try {
                socket.connect(InetSocketAddress(ip, port), TCP_TIMEOUT_MS)
                true
            } catch (e: ConnectException) {
                // ECONNREFUSED: o host existe e respondeu com RST. EHOSTUNREACH: não existe.
                e.message?.contains("ECONNREFUSED") == true || e.message?.contains("refused", ignoreCase = true) == true
            } catch (_: SocketTimeoutException) {
                false
            } catch (_: IOException) {
                false
            }
        }
    }

    companion object {
        /** Portas comuns em redes domésticas/escritório; 62078 é o serviço de sincronização do iPhone. */
        val PROBE_PORTS = listOf(80, 443, 22, 445, 139, 53, 8080, 62078)
        const val TCP_TIMEOUT_MS = 500
    }
}

/** NetBIOS node status (UDP 137): nome e MAC de máquinas Windows/Samba. */
class NetbiosClient @Inject constructor(@param:IoDispatcher private val io: CoroutineDispatcher) {
    suspend fun query(ip: String): Netbios.NodeStatus? = runInterruptible(io) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 800
                val request = Netbios.nodeStatusRequest(transactionId = (System.nanoTime() and 0xFFFF).toInt())
                socket.send(DatagramPacket(request, request.size, InetAddress.getByName(ip), Netbios.PORT))
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                Netbios.parseNodeStatus(buffer.copyOf(packet.length))
            }
        } catch (_: IOException) {
            null
        }
    }
}

/** SSDP: M-SEARCH em multicast e leitura do XML de descrição de cada dispositivo que responder. */
class SsdpClient @Inject constructor(@param:IoDispatcher private val io: CoroutineDispatcher) {

    fun discover(windowMillis: Long = 4_000): Flow<DeviceUpdate> = channelFlow {
        val responders = runInterruptible(io) { search(windowMillis) }
        responders.forEach { (ip, location) ->
            launch {
                val description = fetchDescription(ip, location) ?: return@launch
                send(
                    DeviceUpdate(
                        ip = ip,
                        detection = Detection.Ssdp,
                        hostname = description.friendlyName?.let { Sourced(it, InfoSource.Upnp) },
                        vendor = description.manufacturer?.let { Sourced(it, InfoSource.Upnp) },
                        model = description.modelName,
                    ),
                )
            }
        }
    }

    /** IP → LOCATION de quem respondeu na janela. */
    private fun search(windowMillis: Long): Map<String, String> {
        val found = mutableMapOf<String, String>()
        try {
            DatagramSocket().use { socket ->
                val group = InetAddress.getByName(Ssdp.ADDRESS)
                repeat(2) { socket.send(DatagramPacket(Ssdp.searchRequest, Ssdp.searchRequest.size, group, Ssdp.PORT)) }
                val deadline = System.currentTimeMillis() + windowMillis
                val buffer = ByteArray(2048)
                while (true) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0 || Thread.currentThread().isInterrupted) break
                    socket.soTimeout = remaining.toInt()
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    val ip = packet.address.hostAddress ?: continue
                    Ssdp.location(String(buffer, 0, packet.length, Charsets.UTF_8))?.let { found.putIfAbsent(ip, it) }
                }
            }
        } catch (_: IOException) {
            // Multicast indisponível (rede sem suporte): segue sem UPnP.
        }
        return found
    }

    /** Só busca descrições hospedadas no próprio IP que respondeu, e com tamanho limitado. */
    private suspend fun fetchDescription(ip: String, location: String): Ssdp.Description? = runInterruptible(io) {
        try {
            val url = URL(location)
            if (url.host != ip) return@runInterruptible null
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runInterruptible null
                val body = connection.inputStream.bufferedReader().use { r ->
                    val chars = CharArray(64 * 1024)
                    val n = r.read(chars)
                    if (n > 0) String(chars, 0, n) else ""
                }
                Ssdp.parseDescription(body)
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** mDNS / DNS-SD via NsdManager: nome anunciado, serviços e, quando presente no TXT, o MAC. */
class MdnsBrowser @Inject constructor(@ApplicationContext context: Context) {

    private val nsd = context.getSystemService(NsdManager::class.java)
    /** Antes do Android 14, o NsdManager resolve um serviço por vez. */
    private val resolveLock = Mutex()

    fun browse(): Flow<DeviceUpdate> = channelFlow {
        val manager = nsd ?: return@channelFlow
        SERVICE_TYPES.forEach { type ->
            launch {
                discovered(manager, type).collect { info ->
                    launch {
                        val resolved = resolveLock.withLock { withTimeoutOrNull(3_000) { resolve(manager, info) } } ?: return@launch
                        toUpdate(resolved, type)?.let { send(it) }
                    }
                }
            }
        }
    }

    private fun discovered(manager: NsdManager, type: String): Flow<NsdServiceInfo> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                trySend(serviceInfo)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            try {
                manager.stopServiceDiscovery(listener)
            } catch (_: IllegalArgumentException) {
                // já parado
            }
        }
    }

    @Suppress("DEPRECATION") // resolveService segue funcionando; a alternativa só existe no Android 14+
    private suspend fun resolve(manager: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { cont ->
            manager.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo)
                    }
                },
            )
        }

    @Suppress("DEPRECATION")
    private fun toUpdate(info: NsdServiceInfo, type: String): DeviceUpdate? {
        val ip = info.host?.hostAddress?.takeIf { '.' in it } ?: return null
        val txt = info.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8).orEmpty() }
        val mac = MdnsText.macFrom(info.serviceName, txt)
        return DeviceUpdate(
            ip = ip,
            detection = Detection.Mdns,
            hostname = info.serviceName?.let { MdnsText.cleanName(it) }?.takeIf { it.isNotBlank() }?.let { Sourced(it, InfoSource.Mdns) },
            mac = mac?.let { Sourced(it, InfoSource.Mdns) },
            model = txt["md"] ?: txt["model"] ?: txt["ty"],
            services = setOf(type.trimEnd('.')),
        )
    }

    companion object {
        val SERVICE_TYPES = listOf(
            "_workstation._tcp", "_device-info._tcp", "_http._tcp", "_ipp._tcp", "_printer._tcp",
            "_pdl-datastream._tcp", "_googlecast._tcp", "_airplay._tcp", "_raop._tcp", "_smb._tcp",
            "_ssh._tcp", "_companion-link._tcp", "_hap._tcp", "_spotify-connect._tcp",
        )
    }
}

/** Leitura de MAC e nome a partir dos registros mDNS. Pura. */
object MdnsText {
    private val macPattern = Regex("""([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}""")

    /**
     * MAC real anunciado pelo próprio dispositivo:
     * `_workstation._tcp` usa "nome [aa:bb:cc:dd:ee:ff]"; AirPlay usa `deviceid`; alguns usam `mac`.
     */
    fun macFrom(serviceName: String?, txt: Map<String, String>): String? {
        val candidates = listOfNotNull(
            serviceName?.let { Regex("""\[([^\]]+)]""").find(it)?.groupValues?.get(1) },
            txt["deviceid"], txt["mac"], txt["macaddress"], txt["MAC"],
        )
        return candidates.firstNotNullOfOrNull { macPattern.find(it)?.value }?.uppercase()?.replace('-', ':')
    }

    /** "macbook-ana [3c:22:fb:9a:10:7e]" → "macbook-ana". */
    fun cleanName(serviceName: String): String = serviceName.replace(Regex("""\s*\[[^\]]*]\s*$"""), "").trim()
}
