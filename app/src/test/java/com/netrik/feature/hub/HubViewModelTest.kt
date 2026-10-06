package com.netrik.feature.hub

import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.CurrentNetwork.Transport
import com.netrik.core.network.Ipv4Address
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.network.PublicIpRepository
import com.netrik.core.settings.NetworkPreferences
import com.netrik.core.wifi.WifiNetwork
import com.netrik.core.wifi.WifiScanRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {

    private val network = MutableStateFlow<CurrentNetwork>(connected("192.168.0.42"))
    private val networkRepo = object : NetworkInfoRepository {
        override val currentNetwork = network
    }
    private var publicIpResponse = CompletableDeferred<Result<String>>()
    private var publicIpCalls = 0
    private val noWifiScan = object : WifiScanRepository {
        override val networks = flowOf(emptyList<WifiNetwork>())
        override val wifiEnabled = flowOf(true)
        override val locationEnabled = flowOf(true)
        override val supports6Ghz = false
        override fun requestScan() = false
    }
    private val alwaysShowPublicIp = MutableStateFlow(false)
    private val prefs = object : NetworkPreferences {
        override val alwaysShowPublicIp = this@HubViewModelTest.alwaysShowPublicIp
        override val hideHiddenWifi = flowOf(false)
        override val identifyDevicesByPorts = flowOf(true)
    }
    private val publicIpRepo = object : PublicIpRepository {
        override suspend fun fetchPublicIp(): Result<String> {
            publicIpCalls++
            return publicIpResponse.await()
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `public IP is only queried when the user asks`() = runTest {
        val vm = HubViewModel(networkRepo, publicIpRepo, noWifiScan, prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals(PublicIpUi.Hidden, vm.uiState.value.publicIp)
        assertEquals(0, publicIpCalls)

        vm.onShowPublicIp()
        assertEquals(PublicIpUi.Loading, vm.uiState.value.publicIp)

        publicIpResponse.complete(Result.success("177.92.14.203"))
        assertEquals(PublicIpUi.Loaded("177.92.14.203"), vm.uiState.value.publicIp)
        assertEquals(1, publicIpCalls)
    }

    @Test
    fun `lookup failure is shown and allows retrying`() = runTest {
        val vm = HubViewModel(networkRepo, publicIpRepo, noWifiScan, prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onShowPublicIp()
        publicIpResponse.complete(Result.failure(IOException("no route")))
        assertEquals(PublicIpUi.Failed, vm.uiState.value.publicIp)

        publicIpResponse = CompletableDeferred()
        vm.onShowPublicIp()
        publicIpResponse.complete(Result.success("177.92.14.203"))
        assertEquals(PublicIpUi.Loaded("177.92.14.203"), vm.uiState.value.publicIp)
    }

    @Test
    fun `changing network hides the public IP queried on the previous one`() = runTest {
        val vm = HubViewModel(networkRepo, publicIpRepo, noWifiScan, prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onShowPublicIp()
        publicIpResponse.complete(Result.success("177.92.14.203"))
        assertEquals(PublicIpUi.Loaded("177.92.14.203"), vm.uiState.value.publicIp)

        network.value = connected("10.0.0.5")
        assertEquals(PublicIpUi.Hidden, vm.uiState.value.publicIp)

        network.value = CurrentNetwork.Disconnected
        assertEquals(NetworkCardState.Disconnected, vm.uiState.value.network)
        assertEquals(PublicIpUi.Hidden, vm.uiState.value.publicIp)
    }

    @Test
    fun `no connection does not trigger a lookup`() = runTest {
        network.value = CurrentNetwork.Disconnected
        val vm = HubViewModel(networkRepo, publicIpRepo, noWifiScan, prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onShowPublicIp()
        assertEquals(0, publicIpCalls)
    }

    @Test
    fun `always show public IP looks it up on its own on every network change`() = runTest {
        alwaysShowPublicIp.value = true
        val vm = HubViewModel(networkRepo, publicIpRepo, noWifiScan, prefs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals(1, publicIpCalls)
        publicIpResponse.complete(Result.success("177.92.14.203"))
        assertEquals(PublicIpUi.Loaded("177.92.14.203"), vm.uiState.value.publicIp)

        publicIpResponse = CompletableDeferred()
        network.value = connected("10.0.0.5")
        assertEquals(2, publicIpCalls)
        assertEquals(PublicIpUi.Loading, vm.uiState.value.publicIp)

        // Turning it off doesn't trigger new lookups.
        alwaysShowPublicIp.value = false
        network.value = connected("10.0.0.6")
        assertEquals(2, publicIpCalls)
    }

    private fun connected(ip: String) = CurrentNetwork.Connected(
        transport = Transport.Wifi,
        validated = true,
        ipv4 = Ipv4Address(ip, 24),
        gateway = null,
        dnsServers = emptyList(),
        ipv6 = null,
        wifi = null,
        carrierName = null,
    )
}
