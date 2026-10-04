package com.netrik.feature.oui

import androidx.lifecycle.SavedStateHandle
import com.netrik.core.oui.OuiDbStatus
import com.netrik.core.oui.OuiHistoryEntry
import com.netrik.core.oui.OuiMatch
import com.netrik.core.oui.OuiRecord
import com.netrik.core.oui.OuiRegistry
import com.netrik.core.oui.OuiRepository
import com.netrik.core.oui.OuiUpdateEvent
import com.netrik.core.oui.OuiUpdateState
import com.netrik.feature.devices.PendingDeviceSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class OuiViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val apple = OuiRecord(OuiRegistry.MaL, "3C22FB", "Apple, Inc.", "Cupertino US")

    private val repo = object : OuiRepository {
        val recorded = mutableListOf<String>()
        override val status = MutableStateFlow(OuiDbStatus(1, null, false, OuiUpdateState.Idle))
        override val updateEvents: SharedFlow<OuiUpdateEvent> = MutableSharedFlow()
        override val history = flowOf(emptyList<OuiHistoryEntry>())
        override suspend fun lookup(hex: String) = if (hex.startsWith("3C22FB")) OuiMatch(apple, hex) else null
        override suspend fun lookupMany(hexes: Collection<String>) = emptyMap<String, OuiMatch>()
        override fun updateFromIeee() = Unit
        override suspend fun recordQuery(hex: String) { recorded += hex }
        override suspend fun removeFromHistory(hex: String) = Unit
        override suspend fun clearHistory() = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(mac: String? = null): OuiViewModel {
        val vm = OuiViewModel(repo, Clock.systemUTC(), PendingDeviceSearch(), SavedStateHandle(mapOf("mac" to mac)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `MAC completo é consultado sozinho após a pausa de digitação`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onInputChange("3c-22-fb-9a-10-7e")
        advanceTimeBy(100)
        assertNull(vm.uiState.value.result)

        advanceUntilIdle()
        val result = vm.uiState.value.result as OuiResult.Done
        assertEquals("Apple, Inc.", result.match?.record?.organization)
        assertEquals("3C-22-FB-9A-10-7E", vm.uiState.value.input)
        assertEquals(listOf("3C22FB9A107E"), repo.recorded)
    }

    @Test
    fun `prefixo só é consultado ao confirmar`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onInputChange("3C:22:FB")
        advanceUntilIdle()
        assertNull(vm.uiState.value.result)

        vm.onSubmit()
        advanceUntilIdle()
        assertEquals("3C22FB", vm.uiState.value.result?.hex)
    }

    @Test
    fun `poucos dígitos mostram erro só ao tentar consultar`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onInputChange("3C:22")
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.showShortError)

        vm.onSubmit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showShortError)
        assertNull(vm.uiState.value.result)
    }

    @Test
    fun `MAC aleatório é sinalizado sem fabricante`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onPaste("da:a1:19:6e:03:5c")
        advanceUntilIdle()
        val result = vm.uiState.value.result as OuiResult.Done
        assertTrue(result.locallyAdministered)
        assertNull(result.match)
    }

    @Test
    fun `MAC recebido pela navegação já abre consultado`() = runTest(dispatcher) {
        val vm = viewModel(mac = "3C22FB9A107E")
        advanceUntilIdle()
        assertEquals("Apple, Inc.", (vm.uiState.value.result as OuiResult.Done).match?.record?.organization)
    }
}
