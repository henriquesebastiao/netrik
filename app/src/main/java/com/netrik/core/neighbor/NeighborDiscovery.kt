package com.netrik.core.neighbor

import android.content.Context
import android.net.wifi.WifiManager
import com.netrik.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.time.Clock
import javax.inject.Inject

sealed interface NeighborEvent {
    data class Found(val neighbor: Neighbor) : NeighborEvent

    /** UDP port 5678 couldn't be opened (another app holds it): MNDP answers won't arrive; Ubiquiti still works. */
    data object MndpPortBusy : NeighborEvent
}

/**
 * Asks the local network for MikroTik (MNDP, UDP 5678) and Ubiquiti (UDP 10001) devices and reports every
 * answer, until the collection is cancelled. Requests go to 255.255.255.255 and to each interface's
 * broadcast address: fast at first, then every [SLOW_INTERVAL_MS]. RouterOS also announces itself about
 * once a minute without being asked.
 *
 * Only plain UDP sockets: CDP and LLDP are layer 2 frames, which need raw sockets that Android only allows
 * with root.
 */
class NeighborDiscovery @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock,
) {
    fun discover(): Flow<NeighborEvent> = channelFlow {
        // Without the lock some Wi-Fi drivers filter broadcast/multicast packets to save battery.
        val lock = context.getSystemService(WifiManager::class.java)
            ?.createMulticastLock("netrik-neighbors")
            ?.apply { setReferenceCounted(false); acquire() }
        val mndp = openMndpSocket()
        if (mndp == null) send(NeighborEvent.MndpPortBusy)
        val ubiquiti = withContext(io) { DatagramSocket().apply { broadcast = true } }
        try {
            mndp?.let { socket -> launch { receive(socket) { data, length, ip, now -> Mndp.parse(data, length, ip, now) } } }
            launch { receive(ubiquiti) { data, length, ip, now -> UbiquitiDiscovery.parse(data, length, ip, now) } }
            launch {
                var round = 0
                while (true) {
                    withContext(io) {
                        // Without our own port 5678 the request goes out anyway: devices also announce to everyone.
                        sendToAll(mndp ?: ubiquiti, Mndp.request, Mndp.PORT)
                        sendToAll(ubiquiti, UbiquitiDiscovery.probeV1, UbiquitiDiscovery.PORT)
                        sendToAll(ubiquiti, UbiquitiDiscovery.probeV2, UbiquitiDiscovery.PORT)
                    }
                    delay(if (round++ < FAST_ROUNDS) FAST_INTERVAL_MS else SLOW_INTERVAL_MS)
                }
            }
            awaitCancellation()
        } finally {
            // Closing unblocks the receive() calls, so the readers end too.
            mndp?.close()
            ubiquiti.close()
            lock?.release()
        }
    }

    /** Binds 5678 sharing it (SO_REUSEADDR): the Devices scan and this tool can listen at the same time. */
    private suspend fun openMndpSocket(): DatagramSocket? = withContext(io) {
        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(InetSocketAddress(Mndp.PORT))
            socket
        } catch (_: SocketException) {
            socket.close()
            null
        }
    }

    private suspend fun ProducerScope<NeighborEvent>.receive(
        socket: DatagramSocket,
        parse: (ByteArray, Int, String, Long) -> Neighbor?,
    ) {
        val buffer = ByteArray(MAX_PACKET)
        while (!socket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            val received = withContext(io) {
                try {
                    socket.receive(packet)
                    true
                } catch (_: IOException) {
                    false
                }
            }
            if (!received) break
            val ip = packet.address?.hostAddress?.substringBefore('%') ?: continue
            parse(buffer, packet.length, ip, clock.millis())?.let { send(NeighborEvent.Found(it)) }
        }
    }

    private fun sendToAll(socket: DatagramSocket, payload: ByteArray, port: Int) {
        broadcastAddresses().forEach { address ->
            try {
                socket.send(DatagramPacket(payload, payload.size, address, port))
            } catch (_: IOException) {
                // Network without broadcast (e.g. mobile data) or blocked: nothing to do, the others may work.
            }
        }
    }

    /** 255.255.255.255 plus the broadcast of each active IPv4 interface (some networks only pass the directed one). */
    private fun broadcastAddresses(): Set<InetAddress> {
        val all = mutableSetOf<InetAddress>(InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1)))
        try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNullTo(all) { it.broadcast }
        } catch (_: SocketException) {
            // Interface list unavailable: the limited broadcast is enough.
        }
        return all
    }

    private companion object {
        const val MAX_PACKET = 2048
        const val FAST_ROUNDS = 5
        const val FAST_INTERVAL_MS = 2_000L
        const val SLOW_INTERVAL_MS = 10_000L
    }
}
