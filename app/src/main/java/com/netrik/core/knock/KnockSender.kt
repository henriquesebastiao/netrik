package com.netrik.core.knock

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.netrik.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import java.io.FileDescriptor
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Why a knock couldn't leave the device. */
sealed interface KnockError {
    /** The system refused to send (EPERM/EACCES): firewall, VPN lockdown or a missing permission. */
    data object Blocked : KnockError
    data object Unreachable : KnockError
    data class Other(val detail: String?) : KnockError
}

class KnockSendException(val error: KnockError, cause: Throwable? = null) : IOException(cause)

/** Result of the optional TCP test after the sequence. */
enum class PortCheck { Open, Closed, NoReply }

interface KnockSender {
    /** Sends one knock to [address]; throws [KnockSendException] when the packet can't leave. */
    suspend fun send(address: InetAddress, step: KnockStep)

    suspend fun checkTcp(address: InetAddress, port: Int): PortCheck
}

/**
 * Sends knocks without root:
 * - TCP: a non-blocking connect, closed right away. The kernel sends the SYN during connect(), which is all
 *   a knock server watches; a refusal or no answer is the normal outcome.
 * - UDP: an empty datagram.
 * - ICMP: an echo request through an ICMP datagram socket ("ping socket", allowed for apps on Android; the
 *   kernel fills the identifier and checksum). If the socket can't be created, falls back to `/system/bin/ping`.
 */
class SocketKnockSender @Inject constructor(
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : KnockSender {

    override suspend fun send(address: InetAddress, step: KnockStep): Unit = runInterruptible(io) {
        try {
            when (step.protocol) {
                KnockProtocol.Tcp -> SocketChannel.open().use { channel ->
                    channel.configureBlocking(false)
                    try {
                        channel.connect(InetSocketAddress(address, requireNotNull(step.port)))
                    } catch (_: ConnectException) {
                        // Refused at once (e.g. a local address): the SYN still went out.
                    }
                }
                KnockProtocol.Udp -> DatagramSocket().use { socket ->
                    socket.send(DatagramPacket(ByteArray(0), 0, InetSocketAddress(address, requireNotNull(step.port))))
                }
                KnockProtocol.Icmp -> sendEcho(address, step.payloadSize ?: DEFAULT_PAYLOAD)
            }
        } catch (e: KnockSendException) {
            throw e
        } catch (e: IOException) {
            throw KnockSendException(classify(e), e)
        }
    }

    override suspend fun checkTcp(address: InetAddress, port: Int): PortCheck = runInterruptible(io) {
        try {
            Socket().use { it.connect(InetSocketAddress(address, port), CHECK_TIMEOUT_MS) }
            PortCheck.Open
        } catch (_: ConnectException) {
            PortCheck.Closed
        } catch (_: SocketTimeoutException) {
            PortCheck.NoReply
        } catch (_: IOException) {
            PortCheck.NoReply
        }
    }

    private fun sendEcho(address: InetAddress, payload: Int) {
        val v6 = address is Inet6Address
        val fd: FileDescriptor = try {
            Os.socket(
                if (v6) OsConstants.AF_INET6 else OsConstants.AF_INET,
                OsConstants.SOCK_DGRAM,
                if (v6) OsConstants.IPPROTO_ICMPV6 else OsConstants.IPPROTO_ICMP,
            )
        } catch (_: ErrnoException) {
            return pingBinary(address, payload)
        }
        try {
            // Echo request header: type, code 0, checksum and identifier filled by the kernel, sequence 1.
            val packet = ByteArray(ICMP_HEADER + payload)
            packet[0] = (if (v6) ICMPV6_ECHO_REQUEST else ICMP_ECHO_REQUEST).toByte()
            packet[7] = 1
            Os.sendto(fd, packet, 0, packet.size, 0, address, 0)
        } catch (e: ErrnoException) {
            throw KnockSendException(classify(e.errno, e.message), e)
        } finally {
            Os.close(fd)
        }
    }

    /** Fallback: one echo request through the system ping (same payload size). */
    private fun pingBinary(address: InetAddress, payload: Int) {
        val v6 = address is Inet6Address
        val command = listOf(
            if (v6) "/system/bin/ping6" else "/system/bin/ping",
            "-c", "1", "-W", "1", "-s", payload.toString(), address.hostAddress.orEmpty().substringBefore('%'),
        )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(PING_WAIT_SECONDS, TimeUnit.SECONDS)) return
            // 0 = reply, 1 = no reply (both mean the request left); anything else is an error.
            if (process.exitValue() > 1) {
                val output = process.inputStream.bufferedReader().readText().trim()
                throw KnockSendException(KnockError.Other(output.lineSequence().lastOrNull()))
            }
        } finally {
            process.destroy()
        }
    }

    private fun classify(e: IOException): KnockError {
        val errno = (e.cause as? ErrnoException)?.errno
        return when {
            errno != null -> classify(errno, e.message)
            e is NoRouteToHostException -> KnockError.Unreachable
            e.message?.contains("EPERM") == true || e.message?.contains("EACCES") == true -> KnockError.Blocked
            e.message?.contains("ENETUNREACH") == true || e.message?.contains("EHOSTUNREACH") == true -> KnockError.Unreachable
            else -> KnockError.Other(e.message)
        }
    }

    private fun classify(errno: Int, message: String?): KnockError = when (errno) {
        OsConstants.EPERM, OsConstants.EACCES -> KnockError.Blocked
        OsConstants.ENETUNREACH, OsConstants.EHOSTUNREACH -> KnockError.Unreachable
        else -> KnockError.Other(message)
    }

    private companion object {
        const val ICMP_HEADER = 8
        const val ICMP_ECHO_REQUEST = 8
        const val ICMPV6_ECHO_REQUEST = 128

        /** Same default payload as ping. */
        const val DEFAULT_PAYLOAD = 56
        const val CHECK_TIMEOUT_MS = 3_000
        const val PING_WAIT_SECONDS = 3L
    }
}
