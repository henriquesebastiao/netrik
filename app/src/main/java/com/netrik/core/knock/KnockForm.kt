package com.netrik.core.knock

import com.netrik.core.ssh.SshForm

enum class KnockFieldError { HostRequired, HostInvalid, DelayInvalid, VerifyPortInvalid, NoSteps, TooManySteps }

enum class KnockStepError { PortInvalid, PayloadInvalid }

/** Knock form validation. Steps are validated one by one ([stepErrors]). */
object KnockForm {

    const val MAX_STEPS = 32
    const val DEFAULT_DELAY_MS = 500
    const val MAX_DELAY_MS = 60_000

    /** Largest ICMP echo payload over IPv4 (65535 − 20 IP − 8 ICMP). */
    const val MAX_PAYLOAD = 65_507

    data class Input(
        val host: String,
        val delayMs: String,
        val verifyPort: String,
        val steps: List<StepInput>,
    )

    /** Step as typed: [value] is the port (TCP/UDP) or the payload size (ICMP, may be blank). */
    data class StepInput(val protocol: KnockProtocol, val value: String)

    fun validate(input: Input): Map<KnockField, KnockFieldError> = buildMap {
        val host = input.host.trim()
        when {
            host.isEmpty() -> put(KnockField.Host, KnockFieldError.HostRequired)
            !SshForm.isValidHost(host) -> put(KnockField.Host, KnockFieldError.HostInvalid)
        }
        if (parseDelay(input.delayMs) == null) put(KnockField.Delay, KnockFieldError.DelayInvalid)
        if (input.verifyPort.isNotBlank() && parsePort(input.verifyPort) == null) put(KnockField.VerifyPort, KnockFieldError.VerifyPortInvalid)
        when {
            input.steps.isEmpty() -> put(KnockField.Steps, KnockFieldError.NoSteps)
            input.steps.size > MAX_STEPS -> put(KnockField.Steps, KnockFieldError.TooManySteps)
        }
    }

    /** Error of each step, by index (only the invalid ones). */
    fun stepErrors(steps: List<StepInput>): Map<Int, KnockStepError> = buildMap {
        steps.forEachIndexed { index, step -> if (toStep(step) == null) put(index, errorFor(step.protocol)) }
    }

    fun toStep(input: StepInput): KnockStep? = when (input.protocol) {
        KnockProtocol.Tcp, KnockProtocol.Udp -> parsePort(input.value)?.let { KnockStep(input.protocol, port = it) }
        KnockProtocol.Icmp -> when {
            input.value.isBlank() -> KnockStep(KnockProtocol.Icmp)
            else -> parsePayload(input.value)?.let { KnockStep(KnockProtocol.Icmp, payloadSize = it) }
        }
    }

    fun isValid(step: KnockStep): Boolean = when (step.protocol) {
        KnockProtocol.Tcp, KnockProtocol.Udp -> step.port != null && step.port in 1..65535 && step.payloadSize == null
        KnockProtocol.Icmp -> step.port == null && (step.payloadSize == null || step.payloadSize in 0..MAX_PAYLOAD)
    }

    /** A saved/imported knock that can run as is. */
    fun isValid(profile: KnockProfile): Boolean =
        profile.host.isNotBlank() && SshForm.isValidHost(profile.host.trim()) &&
            profile.delayMs in 0..MAX_DELAY_MS &&
            (profile.verifyPort == null || profile.verifyPort in 1..65535) &&
            profile.steps.size in 1..MAX_STEPS && profile.steps.all(::isValid)

    fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    fun parsePayload(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..MAX_PAYLOAD }

    fun parseDelay(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..MAX_DELAY_MS }

    /** Display name: the one given or, if blank, the host itself. */
    fun displayName(name: String, host: String): String = name.trim().ifEmpty { SshForm.normalizeHost(host) }

    private fun errorFor(protocol: KnockProtocol) =
        if (protocol == KnockProtocol.Icmp) KnockStepError.PayloadInvalid else KnockStepError.PortInvalid
}

enum class KnockField { Host, Delay, VerifyPort, Steps }
