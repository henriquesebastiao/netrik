package com.netrik.core.network.ping

/** Interpreta, linha a linha, a saída do ping do Android (iputils), executado com `-n -O`. */
object PingOutputParser {

    private val header = Regex("""^PING\s+(\S+)\s*\(([^)]+)\)\s+(\d+)""")
    // Origem não-gananciosa: aceita IPv6 ("from ::1: icmp_seq=1").
    private val reply = Regex("""^\d+\s+bytes from\s+(\S+?):?\s+icmp_seq=(\d+)(?:\s+ttl=(\d+))?\s+time=([\d.]+)\s*ms""")
    private val noAnswer = Regex("""^no answer yet for icmp_seq=(\d+)""")
    private val fromError = Regex("""^From\s+(\S+?):?\s+icmp_seq=(\d+)\s+(.+)$""")
    private val summary = Regex("""^(\d+)\s+packets transmitted,\s+(\d+)\s+received""")
    private val failure = Regex("""^(?:ping6?: |connect: )(.+)$""")

    fun parse(rawLine: String): PingEvent? {
        // -D adiciona "[timestamp] " no começo; não usamos, mas toleramos.
        val line = rawLine.trim().replace(Regex("""^\[[\d.]+\]\s*"""), "")
        if (line.isEmpty()) return null

        header.find(line)?.let { m ->
            val (host, address, size) = m.destructured
            return PingEvent.Header(host, address, size.toInt())
        }
        reply.find(line)?.let { m ->
            val (from, seq, ttl, time) = m.destructured
            return PingEvent.Reply(seq.toInt(), from, ttl.toIntOrNull(), time.toDouble())
        }
        noAnswer.find(line)?.let { return PingEvent.NoAnswer(it.groupValues[1].toInt()) }
        fromError.find(line)?.let { m ->
            val (from, seq, reason) = m.destructured
            return if (reason.startsWith("Time to live exceeded", ignoreCase = true) || reason.startsWith("Time exceeded", ignoreCase = true)) {
                PingEvent.TtlExceeded(seq.toInt(), from)
            } else {
                PingEvent.Unreachable(seq.toInt(), from, reason.trim())
            }
        }
        summary.find(line)?.let { m ->
            val (sent, received) = m.destructured
            return PingEvent.Summary(sent.toInt(), received.toInt())
        }
        if (line.contains("unknown host", ignoreCase = true) || line.contains("Name or service not known", ignoreCase = true)) {
            return PingEvent.UnknownHost
        }
        failure.find(line)?.let { return PingEvent.Failure(it.groupValues[1].trim()) }
        return null
    }
}
