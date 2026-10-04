package com.netrik.core.portscan

import com.netrik.core.lan.Netbios
import com.netrik.core.lan.Ssdp

/**
 * UDP probes per protocol. Many UDP services ignore empty packets; a valid request of the
 * protocol raises the chance of a reply (and of classifying the port as actually open).
 */
object UdpProbes {

    fun payloadFor(port: Int): ByteArray = when (port) {
        53, 5353 -> dnsQuery()
        123 -> ntpRequest()
        137 -> Netbios.nodeStatusRequest(0x4E54)
        161 -> snmpGetSysDescr()
        1900 -> Ssdp.searchRequest
        else -> ByteArray(0)
    }

    /** Standard DNS query for "." type NS (small reply on any server). */
    internal fun dnsQuery(): ByteArray = byteArrayOf(
        0x4E, 0x54, // id
        0x01, 0x00, // recursion desired
        0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 1 pergunta
        0x00, // root name
        0x00, 0x02, // tipo NS
        0x00, 0x01, // classe IN
    )

    /** NTPv3 client request (48 bytes, LI=0, VN=3, mode=3). */
    internal fun ntpRequest(): ByteArray = ByteArray(48).also { it[0] = 0x1B }

    /** SNMPv1 GetRequest with community "public" for sysDescr.0 (1.3.6.1.2.1.1.1.0). */
    internal fun snmpGetSysDescr(): ByteArray = byteArrayOf(
        0x30, 0x26,
        0x02, 0x01, 0x00, // version 1
        0x04, 0x06, 0x70, 0x75, 0x62, 0x6C, 0x69, 0x63, // "public"
        0xA0.toByte(), 0x19, // GetRequest
        0x02, 0x01, 0x01, // request-id
        0x02, 0x01, 0x00, // error-status
        0x02, 0x01, 0x00, // error-index
        0x30, 0x0E, 0x30, 0x0C,
        0x06, 0x08, 0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00, // OID
        0x05, 0x00, // NULL
    )
}
