package com.netrik.core.portscan

import com.netrik.core.lan.Netbios
import com.netrik.core.lan.Ssdp

/**
 * Sondas UDP por protocolo. Muitos serviços UDP ignoram pacotes vazios; uma requisição válida
 * do protocolo aumenta a chance de resposta (e de classificar a porta como aberta de fato).
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

    /** Consulta DNS padrão por "." tipo NS (resposta pequena em qualquer servidor). */
    internal fun dnsQuery(): ByteArray = byteArrayOf(
        0x4E, 0x54, // id
        0x01, 0x00, // recursão desejada
        0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 1 pergunta
        0x00, // nome raiz
        0x00, 0x02, // tipo NS
        0x00, 0x01, // classe IN
    )

    /** Requisição NTPv3 de cliente (48 bytes, LI=0, VN=3, modo=3). */
    internal fun ntpRequest(): ByteArray = ByteArray(48).also { it[0] = 0x1B }

    /** SNMPv1 GetRequest da community "public" por sysDescr.0 (1.3.6.1.2.1.1.1.0). */
    internal fun snmpGetSysDescr(): ByteArray = byteArrayOf(
        0x30, 0x26,
        0x02, 0x01, 0x00, // versão 1
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
