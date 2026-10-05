package com.netrik.core.portscan

enum class RiskLevel { High, Medium }

/** Why a service is commonly risky when exposed; the UI turns each into a translated explanation. */
enum class RiskReason(val level: RiskLevel) {
    Telnet(RiskLevel.High),
    RemoteShell(RiskLevel.High),
    Smb(RiskLevel.High),
    RemoteDesktop(RiskLevel.High),
    Vnc(RiskLevel.High),
    DockerApi(RiskLevel.High),
    Adb(RiskLevel.High),
    UnauthenticatedStore(RiskLevel.High),
    SmartInstall(RiskLevel.High),
    Ftp(RiskLevel.Medium),
    Tftp(RiskLevel.Medium),
    NetbiosRpc(RiskLevel.Medium),
    Database(RiskLevel.Medium),
    Snmp(RiskLevel.Medium),
    Upnp(RiskLevel.Medium),
    Tr069(RiskLevel.Medium),
    MikrotikManagement(RiskLevel.Medium),
    Mqtt(RiskLevel.Medium),
    Nfs(RiskLevel.Medium),
    WinRm(RiskLevel.Medium),
}

/**
 * Services that are a common risk when reachable: cleartext logins, remote control, storage often left without
 * authentication. A mark means "check whether this should be exposed", not a confirmed vulnerability.
 */
object PortRisks {

    fun of(protocol: Protocol, port: Int): RiskReason? = when (protocol) {
        Protocol.Tcp -> TCP[port]
        Protocol.Udp -> UDP[port]
    }

    private val TCP: Map<Int, RiskReason> = buildMap {
        put(21, RiskReason.Ftp)
        listOf(23, 2323).forEach { put(it, RiskReason.Telnet) }
        listOf(512, 513, 514).forEach { put(it, RiskReason.RemoteShell) }
        put(445, RiskReason.Smb)
        listOf(135, 139).forEach { put(it, RiskReason.NetbiosRpc) }
        put(3389, RiskReason.RemoteDesktop)
        listOf(5900, 5901, 5902).forEach { put(it, RiskReason.Vnc) }
        put(2375, RiskReason.DockerApi)
        put(5555, RiskReason.Adb)
        // Redis, MongoDB, Elasticsearch, Memcached, CouchDB.
        listOf(6379, 27017, 9200, 11211, 5984).forEach { put(it, RiskReason.UnauthenticatedStore) }
        put(4786, RiskReason.SmartInstall)
        // SQL Server, Oracle, MySQL, PostgreSQL.
        listOf(1433, 1521, 3306, 5432).forEach { put(it, RiskReason.Database) }
        put(7547, RiskReason.Tr069)
        // Winbox and the cleartext RouterOS API.
        listOf(8291, 8728).forEach { put(it, RiskReason.MikrotikManagement) }
        put(1883, RiskReason.Mqtt)
        listOf(111, 2049).forEach { put(it, RiskReason.Nfs) }
        put(5985, RiskReason.WinRm)
    }

    private val UDP: Map<Int, RiskReason> = buildMap {
        put(69, RiskReason.Tftp)
        listOf(137, 138).forEach { put(it, RiskReason.NetbiosRpc) }
        put(161, RiskReason.Snmp)
        put(1900, RiskReason.Upnp)
        put(11211, RiskReason.UnauthenticatedStore)
        listOf(111, 2049).forEach { put(it, RiskReason.Nfs) }
    }
}
