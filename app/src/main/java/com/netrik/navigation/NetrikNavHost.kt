package com.netrik.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.netrik.feature.devices.DeviceDetailScreen
import com.netrik.feature.devices.DevicesScreen
import com.netrik.feature.devices.DevicesViewModel
import com.netrik.feature.hub.HubScreen
import com.netrik.feature.oui.OuiScreen
import com.netrik.feature.ping.PingScreen
import com.netrik.feature.portscan.PortScanScreen
import com.netrik.feature.traceroute.TracerouteScreen
import com.netrik.feature.wifi.WifiScreen
import com.netrik.feature.ssh.SshFormScreen
import com.netrik.feature.ssh.SshHostsScreen
import com.netrik.feature.ssh.SshViewModel
import com.netrik.feature.placeholder.ToolPlaceholderScreen

@Composable
fun NetrikNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = ToolsGraph, modifier = modifier) {
        navigation<ToolsGraph>(startDestination = HubRoute) {
            composable<HubRoute> {
                HubScreen(onOpenTool = { tool -> navController.openTool(tool, TopLevelDestination.Tools) })
            }
        }
        navigation<DevicesGraph>(startDestination = DevicesRoute) {
            // Lista e detalhes compartilham o ViewModel do grafo: a varredura sobrevive à navegação.
            composable<DevicesRoute> { entry ->
                DevicesScreen(
                    viewModel = devicesViewModel(navController, entry),
                    onOpenDevice = { ip -> navController.navigate(DeviceDetailRoute(ip)) },
                )
            }
            composable<DeviceDetailRoute> { entry ->
                DeviceDetailScreen(
                    ip = entry.toRoute<DeviceDetailRoute>().ip,
                    viewModel = devicesViewModel(navController, entry),
                    onBack = { navController.popBackStack() },
                    onAction = { tool, ip -> navController.openTool(tool, TopLevelDestination.Devices, ip) },
                )
            }
        }
        navigation<WifiGraph>(startDestination = WifiRoute) {
            composable<WifiRoute> { WifiScreen() }
        }
        navigation<SshGraph>(startDestination = SshRoute) {
            // Lista e formulário compartilham o ViewModel do grafo (conexão em andamento, diálogos).
            composable<SshRoute> { entry ->
                SshHostsScreen(
                    viewModel = sshViewModel(navController, entry),
                    onNewHost = { navController.navigate(SshFormRoute()) },
                    onEditHost = { id, rejected -> navController.navigate(SshFormRoute(hostId = id, passwordRejected = rejected)) },
                )
            }
            composable<SshFormRoute> { entry ->
                val route = entry.toRoute<SshFormRoute>()
                val viewModel = sshViewModel(navController, entry)
                LaunchedEffect(entry.id) { viewModel.prepareForm(entry.id, route.hostId, route.target, route.passwordRejected) }
                SshFormScreen(viewModel = viewModel, onClose = { navController.popBackStack() })
            }
        }
        composable<OuiRoute> {
            OuiScreen(
                onBack = { navController.popBackStack() },
                onFindInNetwork = { navController.navigateToTab(TopLevelDestination.Devices) },
            )
        }
        composable<PingRoute> { PingScreen(onBack = { navController.popBackStack() }) }
        composable<TracerouteRoute> { TracerouteScreen(onBack = { navController.popBackStack() }) }
        composable<PortScanRoute> { PortScanScreen(onBack = { navController.popBackStack() }) }
        composable<ToolRoute> { entry ->
            val route = entry.toRoute<ToolRoute>()
            ToolPlaceholderScreen(tool = route.tool, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun devicesViewModel(navController: NavController, entry: NavBackStackEntry): DevicesViewModel {
    val parent = remember(entry) { navController.getBackStackEntry(DevicesGraph) }
    return hiltViewModel(parent)
}

@Composable
private fun sshViewModel(navController: NavController, entry: NavBackStackEntry): SshViewModel {
    val parent = remember(entry) { navController.getBackStackEntry(SshGraph) }
    return hiltViewModel(parent)
}

/** Rota inicial de cada aba. */
val TopLevelDestination.startRoute: Any
    get() = when (this) {
        TopLevelDestination.Tools -> HubRoute
        TopLevelDestination.Devices -> DevicesRoute
        TopLevelDestination.Wifi -> WifiRoute
        TopLevelDestination.Ssh -> SshRoute
    }

/** Troca de aba salvando a pilha da aba atual e restaurando a da aba de destino. */
fun NavController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Abas viram troca de aba; as demais ferramentas abrem por cima da aba de origem. */
fun NavController.openTool(tool: NetrikTool, origin: TopLevelDestination, target: String? = null) {
    val tab = tool.tab
    when {
        // SSH com alvo (ação rápida de um dispositivo): abre a aba e o formulário já com o host.
        tool == NetrikTool.Ssh && target != null -> {
            navigateToTab(TopLevelDestination.Ssh)
            navigate(SshFormRoute(target = target))
        }
        tab != null -> navigateToTab(tab)
        tool == NetrikTool.Oui -> navigate(OuiRoute(origin = origin, mac = target))
        tool == NetrikTool.Ping -> navigate(PingRoute(origin = origin, target = target))
        tool == NetrikTool.Traceroute -> navigate(TracerouteRoute(origin = origin, target = target))
        tool == NetrikTool.PortScanner -> navigate(PortScanRoute(origin = origin, target = target))
        else -> navigate(ToolRoute(tool = tool, origin = origin, target = target))
    }
}

/** Aba que deve aparecer destacada para esta entrada da pilha. */
fun NavBackStackEntry.topLevelDestination(): TopLevelDestination? {
    if (destination.hasRoute<ToolRoute>()) return toRoute<ToolRoute>().origin
    if (destination.hasRoute<OuiRoute>()) return toRoute<OuiRoute>().origin
    if (destination.hasRoute<PingRoute>()) return toRoute<PingRoute>().origin
    if (destination.hasRoute<TracerouteRoute>()) return toRoute<TracerouteRoute>().origin
    if (destination.hasRoute<PortScanRoute>()) return toRoute<PortScanRoute>().origin
    return TopLevelDestination.entries.firstOrNull { tab ->
        destination.hierarchy.any { it.hasRoute(tab.graphClass) }
    }
}
