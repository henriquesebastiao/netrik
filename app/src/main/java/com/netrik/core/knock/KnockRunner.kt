package com.netrik.core.knock

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.InetAddress

sealed interface KnockEvent {
    data class StepSent(val index: Int) : KnockEvent
    /** The sequence stops at the first knock that can't leave: the rest would be useless. */
    data class StepFailed(val index: Int, val error: KnockError) : KnockEvent
    data class Verifying(val port: Int) : KnockEvent
    data class Verified(val port: Int, val result: PortCheck) : KnockEvent
}

/** Runs a knock sequence: steps in order, [KnockProfile.delayMs] apart, then the optional TCP test. */
object KnockRunner {

    /** Minimum wait before the TCP test, so the server has time to open the port. */
    const val VERIFY_WAIT_MS = 500L

    fun run(profile: KnockProfile, address: InetAddress, sender: KnockSender): Flow<KnockEvent> = flow {
        profile.steps.forEachIndexed { index, step ->
            if (index > 0 && profile.delayMs > 0) delay(profile.delayMs.toLong())
            try {
                sender.send(address, step)
            } catch (e: KnockSendException) {
                emit(KnockEvent.StepFailed(index, e.error))
                return@flow
            }
            emit(KnockEvent.StepSent(index))
        }
        val port = profile.verifyPort ?: return@flow
        delay(maxOf(VERIFY_WAIT_MS, profile.delayMs.toLong()))
        emit(KnockEvent.Verifying(port))
        emit(KnockEvent.Verified(port, sender.checkTcp(address, port)))
    }
}
