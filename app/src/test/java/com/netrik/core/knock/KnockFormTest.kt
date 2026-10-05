package com.netrik.core.knock

import com.netrik.core.knock.KnockForm.StepInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnockFormTest {

    private fun input(host: String = "vps.example.com", delay: String = "500", verify: String = "", steps: List<StepInput> = listOf(StepInput(KnockProtocol.Tcp, "7000"))) =
        KnockForm.Input(host, delay, verify, steps)

    @Test
    fun `valid form has no errors`() {
        assertTrue(KnockForm.validate(input()).isEmpty())
        assertTrue(KnockForm.validate(input(host = "[2001:db8::1]", delay = "0", verify = "22")).isEmpty())
    }

    @Test
    fun `host, delay, port to test and steps are checked`() {
        assertEquals(KnockFieldError.HostRequired, KnockForm.validate(input(host = "  "))[KnockField.Host])
        assertEquals(KnockFieldError.HostInvalid, KnockForm.validate(input(host = "http://x"))[KnockField.Host])
        assertEquals(KnockFieldError.DelayInvalid, KnockForm.validate(input(delay = ""))[KnockField.Delay])
        assertEquals(KnockFieldError.DelayInvalid, KnockForm.validate(input(delay = "60001"))[KnockField.Delay])
        assertEquals(KnockFieldError.VerifyPortInvalid, KnockForm.validate(input(verify = "0"))[KnockField.VerifyPort])
        assertEquals(KnockFieldError.NoSteps, KnockForm.validate(input(steps = emptyList()))[KnockField.Steps])
        val tooMany = List(KnockForm.MAX_STEPS + 1) { StepInput(KnockProtocol.Udp, "1") }
        assertEquals(KnockFieldError.TooManySteps, KnockForm.validate(input(steps = tooMany))[KnockField.Steps])
    }

    @Test
    fun `TCP and UDP need a port, ICMP takes an optional payload size`() {
        assertEquals(KnockStep(KnockProtocol.Tcp, port = 7000), KnockForm.toStep(StepInput(KnockProtocol.Tcp, " 7000 ")))
        assertEquals(KnockStep(KnockProtocol.Udp, port = 65535), KnockForm.toStep(StepInput(KnockProtocol.Udp, "65535")))
        assertNull(KnockForm.toStep(StepInput(KnockProtocol.Tcp, "")))
        assertNull(KnockForm.toStep(StepInput(KnockProtocol.Udp, "65536")))
        assertEquals(KnockStep(KnockProtocol.Icmp), KnockForm.toStep(StepInput(KnockProtocol.Icmp, "")))
        assertEquals(KnockStep(KnockProtocol.Icmp, payloadSize = 0), KnockForm.toStep(StepInput(KnockProtocol.Icmp, "0")))
        assertNull(KnockForm.toStep(StepInput(KnockProtocol.Icmp, "65508")))

        val errors = KnockForm.stepErrors(
            listOf(StepInput(KnockProtocol.Tcp, "1"), StepInput(KnockProtocol.Udp, "0"), StepInput(KnockProtocol.Icmp, "99999")),
        )
        assertEquals(mapOf(1 to KnockStepError.PortInvalid, 2 to KnockStepError.PayloadInvalid), errors)
    }

    @Test
    fun `saved or imported knocks are checked as a whole`() {
        val ok = KnockProfile(0, "VPS", "vps.example.com", null, 500, 22, listOf(KnockStep(KnockProtocol.Tcp, port = 1)))
        assertTrue(KnockForm.isValid(ok))
        assertFalse(KnockForm.isValid(ok.copy(steps = emptyList())))
        assertFalse(KnockForm.isValid(ok.copy(steps = listOf(KnockStep(KnockProtocol.Icmp, port = 22)))))
        assertFalse(KnockForm.isValid(ok.copy(steps = listOf(KnockStep(KnockProtocol.Udp)))))
        assertFalse(KnockForm.isValid(ok.copy(delayMs = -1)))
        assertFalse(KnockForm.isValid(ok.copy(host = "a b")))
    }

    @Test
    fun `name falls back to the host`() {
        assertEquals("10.0.0.1", KnockForm.displayName("  ", "[10.0.0.1]"))
        assertEquals("Lab", KnockForm.displayName(" Lab ", "10.0.0.1"))
    }

    @Test
    fun `sequence summary`() {
        val steps = listOf(
            KnockStep(KnockProtocol.Tcp, port = 7000),
            KnockStep(KnockProtocol.Udp, port = 8000),
            KnockStep(KnockProtocol.Icmp),
            KnockStep(KnockProtocol.Icmp, payloadSize = 64),
        )
        assertEquals("TCP 7000 → UDP 8000 → ICMP → ICMP 64 B", steps.summary { "ICMP $it B" })
    }
}
