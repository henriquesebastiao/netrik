package com.netrik.core.network.ping

/** Opções do ping já validadas. [count] null = contínuo. */
data class PingOptions(
    val count: Int?,
    val intervalSeconds: Double,
    val payloadBytes: Int,
    val timeoutSeconds: Int,
)

data class TracerouteOptions(val maxHops: Int, val hopTimeoutSeconds: Int)

enum class OptionField { Count, Interval, Size, Timeout, MaxHops, HopTimeout }

/** Valida o texto dos campos avançados. Limites do ping do Android para app sem root. */
object PingOptionsValidator {

    const val MIN_INTERVAL_SECONDS = 0.2
    const val MAX_PAYLOAD_BYTES = 65_507

    /** [count] null = modo contínuo. */
    fun ping(count: String?, interval: String, size: String, timeout: String): Result<PingOptions, Set<OptionField>> {
        val errors = mutableSetOf<OptionField>()
        val c = count?.let { errors.check(OptionField.Count, it.int()?.takeIf { n -> n in 1..100_000 }) }
        val i = errors.check(OptionField.Interval, interval.decimal()?.takeIf { it in MIN_INTERVAL_SECONDS..3600.0 })
        val s = errors.check(OptionField.Size, size.int()?.takeIf { it in 0..MAX_PAYLOAD_BYTES })
        val t = errors.check(OptionField.Timeout, timeout.int()?.takeIf { it in 1..60 })
        if (errors.isNotEmpty() || i == null || s == null || t == null) return Result.Invalid(errors)
        return Result.Ok(PingOptions(c, i, s, t))
    }

    fun traceroute(maxHops: String, hopTimeout: String): Result<TracerouteOptions, Set<OptionField>> {
        val errors = mutableSetOf<OptionField>()
        val h = errors.check(OptionField.MaxHops, maxHops.int()?.takeIf { it in 1..64 })
        val t = errors.check(OptionField.HopTimeout, hopTimeout.int()?.takeIf { it in 1..30 })
        if (h == null || t == null) return Result.Invalid(errors)
        return Result.Ok(TracerouteOptions(h, t))
    }

    private fun <T> MutableSet<OptionField>.check(field: OptionField, value: T?): T? {
        if (value == null) add(field)
        return value
    }

    private fun String.int(): Int? = trim().toIntOrNull()

    /** Aceita vírgula decimal ("0,5"). */
    private fun String.decimal(): Double? = trim().replace(',', '.').toDoubleOrNull()

    sealed interface Result<out T, out E> {
        data class Ok<T>(val value: T) : Result<T, Nothing>
        data class Invalid<E>(val errors: E) : Result<Nothing, E>
    }
}

/** Monta a linha de comando do ping. Pura, para ser testada. */
object PingCommand {

    fun build(address: String, ipv6: Boolean, options: PingOptions): List<String> = buildList {
        add(if (ipv6) "/system/bin/ping6" else "/system/bin/ping")
        add("-n") // sem DNS reverso na saída: o app resolve à parte
        add("-O") // reporta pacotes sem resposta
        options.count?.let { add("-c"); add(it.toString()) }
        add("-i"); add(formatInterval(options.intervalSeconds))
        add("-s"); add(options.payloadBytes.toString())
        add("-W"); add(options.timeoutSeconds.toString())
        add(address)
    }

    /** Um único pacote com TTL limitado, para descobrir o salto [ttl] do traceroute. */
    fun probe(address: String, ipv6: Boolean, ttl: Int?, timeoutSeconds: Int): List<String> = buildList {
        add(if (ipv6) "/system/bin/ping6" else "/system/bin/ping")
        add("-n")
        add("-c"); add("1")
        add("-W"); add(timeoutSeconds.toString())
        ttl?.let { add("-t"); add(it.toString()) }
        add(address)
    }

    private fun formatInterval(seconds: Double): String =
        if (seconds == seconds.toLong().toDouble()) seconds.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, seconds).trimEnd('0')
}
