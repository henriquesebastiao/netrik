package com.netrik.core.knock

enum class KnockProtocol(val storedName: String, val label: String) {
    Tcp("tcp", "TCP"),
    Udp("udp", "UDP"),
    Icmp("icmp", "ICMP"),
    ;

    companion object {
        fun fromStored(name: String): KnockProtocol? = entries.firstOrNull { it.storedName == name.trim().lowercase() }
    }
}

/**
 * One knock of the sequence. TCP and UDP go to [port]; ICMP has no port, so an echo request is sent
 * and [payloadSize] (optional) sets its payload length, which is what firewalls usually match on
 * (e.g. `iptables -m length`).
 */
data class KnockStep(
    val protocol: KnockProtocol,
    val port: Int? = null,
    val payloadSize: Int? = null,
)

data class KnockGroup(val id: Long, val name: String, val expanded: Boolean)

/** Saved knock sequence. [verifyPort]: TCP port tested after the sequence, if any. */
data class KnockProfile(
    val id: Long,
    val name: String,
    val host: String,
    val groupId: Long?,
    val delayMs: Int,
    val verifyPort: Int?,
    val steps: List<KnockStep>,
)

/** Same knock, ignoring ids and group: used to skip duplicates on import. */
fun KnockProfile.sameKnockAs(other: KnockProfile): Boolean =
    name.trim() == other.name.trim() &&
        host.trim().equals(other.host.trim(), ignoreCase = true) &&
        steps == other.steps

/** "TCP 7000 → UDP 8000 → ICMP"; [icmpWithSize] formats an ICMP step with a payload size (translated text). */
fun List<KnockStep>.summary(icmpWithSize: (Int) -> String): String = joinToString(" → ") { step ->
    when (step.protocol) {
        KnockProtocol.Tcp, KnockProtocol.Udp -> "${step.protocol.label} ${step.port}"
        KnockProtocol.Icmp -> step.payloadSize?.let(icmpWithSize) ?: step.protocol.label
    }
}
