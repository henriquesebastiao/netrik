package com.netrik.core.knock

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class KnockRunnerTest {

    private val address: InetAddress = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1))

    /** Records each knock with the virtual time it was sent. */
    private class FakeSender(
        private val scope: TestScope,
        private val failAt: Int? = null,
        private val check: PortCheck = PortCheck.Open,
    ) : KnockSender {
        val sent = mutableListOf<Pair<Long, KnockStep>>()
        var checked: Pair<Long, Int>? = null

        override suspend fun send(address: InetAddress, step: KnockStep) {
            if (sent.size == failAt) throw KnockSendException(KnockError.Blocked)
            sent += scope.currentTime to step
        }

        override suspend fun checkTcp(address: InetAddress, port: Int): PortCheck {
            checked = scope.currentTime to port
            return check
        }
    }

    private val steps = listOf(
        KnockStep(KnockProtocol.Tcp, port = 7000),
        KnockStep(KnockProtocol.Udp, port = 8000),
        KnockStep(KnockProtocol.Icmp),
    )

    private fun profile(delay: Int, verify: Int? = null) = KnockProfile(1, "VPS", "10.0.0.1", null, delay, verify, steps)

    @Test
    fun `steps go out in order with the delay between them`() = runTest {
        val sender = FakeSender(this)
        val events = KnockRunner.run(profile(delay = 300), address, sender).toList()
        assertEquals(listOf(0L to steps[0], 300L to steps[1], 600L to steps[2]), sender.sent)
        assertEquals((0..2).map { KnockEvent.StepSent(it) }, events)
        assertEquals(null, sender.checked)
    }

    @Test
    fun `a failed knock stops the sequence`() = runTest {
        val sender = FakeSender(this, failAt = 1)
        val events = KnockRunner.run(profile(delay = 100, verify = 22), address, sender).toList()
        assertEquals(listOf(KnockEvent.StepSent(0), KnockEvent.StepFailed(1, KnockError.Blocked)), events)
        assertEquals(1, sender.sent.size)
        assertEquals(null, sender.checked)
    }

    @Test
    fun `the port test runs after a short wait`() = runTest {
        val sender = FakeSender(this, check = PortCheck.Closed)
        val events = KnockRunner.run(profile(delay = 0, verify = 22), address, sender).toList()
        assertEquals(KnockEvent.Verifying(22), events[3])
        assertEquals(KnockEvent.Verified(22, PortCheck.Closed), events[4])
        assertEquals(KnockRunner.VERIFY_WAIT_MS to 22, sender.checked)
    }
}
