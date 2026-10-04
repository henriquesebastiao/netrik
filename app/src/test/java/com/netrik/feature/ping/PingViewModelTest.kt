package com.netrik.feature.ping

import androidx.lifecycle.SavedStateHandle
import com.netrik.core.database.TargetHistoryDao
import com.netrik.core.database.TargetHistoryEntity
import com.netrik.core.database.TargetHistoryRepository
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.network.ping.HostResolver
import com.netrik.core.network.ping.OptionField
import com.netrik.core.network.ping.PingEvent
import com.netrik.core.network.ping.PingOutputParser
import com.netrik.core.network.ping.PingRunner
import com.netrik.core.network.ping.ResolvedHost
import com.netrik.core.ui.RunFailure
import com.netrik.core.ui.RunPhase
import com.netrik.core.ui.TargetError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class PingViewModelTest {

    private val network = MutableStateFlow<CurrentNetwork>(
        CurrentNetwork.Connected(CurrentNetwork.Transport.Wifi, true, null, null, emptyList(), null, null, null),
    )
    private val lines = Channel<String>(Channel.UNLIMITED)
    private var lastCommand: List<String> = emptyList()

    private val runner = object : PingRunner {
        override fun run(command: List<String>): Flow<PingEvent> {
            lastCommand = command
            return flow {
                emitAll(lines.consumeAsFlow().mapNotNull(PingOutputParser::parse))
                emit(PingEvent.Exited(0))
            }
        }
    }
    private val resolver = object : HostResolver {
        override suspend fun resolve(target: String) =
            if (target.endsWith(".invalid")) null else ResolvedHost(target, "8.8.8.8", ipv6 = false)
        override suspend fun reverse(address: String): String? = null
    }
    private val historyDao = object : TargetHistoryDao {
        val saved = MutableStateFlow<List<TargetHistoryEntity>>(emptyList())
        override fun observe(tool: String, limit: Int) = saved
        override suspend fun upsert(entry: TargetHistoryEntity) { saved.value = saved.value + entry }
        override suspend fun trim(tool: String, keep: Int) = Unit
        override suspend fun clear(tool: String) { saved.value = emptyList() }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): PingViewModel {
        val vm = PingViewModel(
            runner, resolver, TargetHistoryRepository(historyDao, Clock.systemUTC()),
            object : NetworkInfoRepository { override val currentNetwork = network },
            SavedStateHandle(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `execução por quantidade completa timeouts pelo resumo`() = runTest {
        val vm = viewModel()
        vm.onTargetChange("google.com")
        vm.onCountChange("3")
        vm.onStart()
        assertEquals(RunPhase.Running, vm.uiState.value.phase)
        assertEquals("-c", lastCommand[3])

        lines.send("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.0 ms")
        lines.send("64 bytes from 8.8.8.8: icmp_seq=2 ttl=117 time=14.0 ms")
        // o pacote 3 ficou sem resposta: só aparece no resumo
        lines.send("3 packets transmitted, 2 received, 33% packet loss, time 2003ms")
        lines.close()

        val state = vm.uiState.value
        assertEquals(listOf(1, 2, 3), state.samples.map { it.seq })
        assertEquals(null, state.samples.last().timeMs)
        assertEquals(33, state.stats.lossPercent)
        assertEquals(listOf("google.com"), historyDao.saved.value.map { it.target })
    }

    @Test
    fun `fim do processo marca como concluído`() = runTest {
        val vm = viewModel()
        vm.onTargetChange("8.8.8.8")
        vm.onStart()
        lines.send("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.0 ms")
        lines.close() // o processo termina
        assertEquals(RunPhase.Done, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.stats.received)
    }

    @Test
    fun `host inexistente mostra erro no campo`() = runTest {
        val vm = viewModel()
        vm.onTargetChange("exemplo.invalid")
        vm.onStart()
        val state = vm.uiState.value
        assertEquals(RunPhase.Failed, state.phase)
        assertEquals(RunFailure.HostNotFound, state.failure)
        assertEquals(TargetError.Unresolved("exemplo.invalid"), state.targetError)
    }

    @Test
    fun `alvo vazio ou inválido não inicia`() = runTest {
        val vm = viewModel()
        vm.onStart()
        assertEquals(TargetError.Required, vm.uiState.value.targetError)
        vm.onTargetChange("google com")
        vm.onStart()
        assertEquals(TargetError.Invalid("google com"), vm.uiState.value.targetError)
        assertEquals(RunPhase.Idle, vm.uiState.value.phase)
    }

    @Test
    fun `intervalo abaixo do mínimo abre as opções com erro`() = runTest {
        val vm = viewModel()
        vm.onTargetChange("8.8.8.8")
        vm.onToggleAdvanced()
        vm.onIntervalChange("0.1")
        vm.onStart()
        assertTrue(OptionField.Interval in vm.uiState.value.optionErrors)
        assertTrue(vm.uiState.value.advancedOpen)
        assertEquals(RunPhase.Idle, vm.uiState.value.phase)
    }

    @Test
    fun `parar interrompe a execução contínua`() = runTest {
        val vm = viewModel()
        vm.onTargetChange("8.8.8.8")
        vm.onModeChange(PingMode.Continuous)
        vm.onStart()
        assertTrue("-c" !in lastCommand)
        lines.send("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.0 ms")
        vm.onStop()
        assertEquals(RunPhase.Stopped, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.samples.size)
    }

    @Test
    fun `queda da rede durante a execução vira erro de conexão perdida`() = runTest {
        val vm = viewModel()
        vm.onTargetChange("8.8.8.8")
        vm.onStart()
        network.value = CurrentNetwork.Disconnected
        assertEquals(RunPhase.Failed, vm.uiState.value.phase)
        assertEquals(RunFailure.ConnectionLost(), vm.uiState.value.failure)
        assertEquals(false, vm.uiState.value.connected)
    }
}
