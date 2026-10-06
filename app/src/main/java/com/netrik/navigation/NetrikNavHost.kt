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
import com.netrik.feature.knock.KnockFormScreen
import com.netrik.feature.knock.KnockListScreen
import com.netrik.feature.neighbors.NeighborsScreen
import com.netrik.feature.subnet.SubnetScreen
import com.netrik.feature.oui.OuiScreen
import com.netrik.feature.ping.PingScreen
import com.netrik.feature.portscan.PortScanScreen
import com.netrik.feature.traceroute.TracerouteScreen
import com.netrik.feature.wifi.WifiMeterScreen
import com.netrik.feature.wifi.WifiScreen
import com.netrik.feature.settings.SettingsScreen
import com.netrik.feature.ssh.SshFormScreen
import com.netrik.feature.ssh.SshHostsScreen
import com.netrik.feature.ssh.SshTerminalScreen
import com.netrik.feature.ssh.SshViewModel
import com.netrik.feature.placeholder.ToolPlaceholderScreen

@Composable
fun NetrikNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = ToolsGraph, modifier = modifier) {
        navigation<ToolsGraph>(startDestination = HubRoute) {
            composable<HubRoute> {
                HubScreen(
                    onOpenTool = { tool -> navController.openTool(tool, TopLevelDestination.Tools) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onCalculateSubnet = { cidr -> navController.openTool(NetrikTool.SubnetCalculator, TopLevelDestination.Tools, cidr) },
                )
            }
        }
        navigation<DevicesGraph>(startDestination = DevicesRoute) {
            // List and details share the graph ViewModel: the scan survives navigation.
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
            composable<WifiRoute> { WifiScreen(onOpenMeter = { navController.navigate(WifiMeterRoute) }) }
            composable<WifiMeterRoute> { WifiMeterScreen(onBack = { navController.popBackStack() }) }
        }
        navigation<SshGraph>(startDestination = SshRoute) {
            // List, form and terminal share the graph ViewModel (ongoing connection, dialogs, sessions).
            composable<SshRoute> { entry ->
                SshHostsScreen(
                    viewModel = sshViewModel(navController, entry),
                    onNewHost = { navController.navigate(SshFormRoute()) },
                    onEditHost = { id, rejected -> navController.navigate(SshFormRoute(hostId = id, passwordRejected = rejected)) },
                    onOpenTerminal = { navController.navigate(SshTerminalRoute) { launchSingleTop = true } },
                )
            }
            composable<SshTerminalRoute> { entry ->
                SshTerminalScreen(viewModel = sshViewModel(navController, entry), onBack = { navController.popBackStack(SshRoute, inclusive = false) })
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
        composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable<PingRoute> { PingScreen(onBack = { navController.popBackStack() }) }
        composable<TracerouteRoute> { TracerouteScreen(onBack = { navController.popBackStack() }) }
        composable<PortScanRoute> { PortScanScreen(onBack = { navController.popBackStack() }) }
        composable<SubnetRoute> { SubnetScreen(onBack = { navController.popBackStack() }) }
        composable<NeighborsRoute> { entry ->
            val origin = entry.toRoute<NeighborsRoute>().origin
            NeighborsScreen(
                onBack = { navController.popBackStack() },
                onAction = { tool, target -> navController.openTool(tool, origin, target) },
            )
        }
        composable<KnockRoute> { entry ->
            val origin = entry.toRoute<KnockRoute>().origin
            KnockListScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                onNewKnock = { navController.navigate(KnockFormRoute(origin)) },
                onEditKnock = { id -> navController.navigate(KnockFormRoute(origin, profileId = id)) },
            )
        }
        composable<KnockFormRoute> { KnockFormScreen(viewModel = hiltViewModel(), onClose = { navController.popBackStack() }) }
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

/** Start route of each tab. */
val TopLevelDestination.startRoute: Any
    get() = when (this) {
        TopLevelDestination.Tools -> HubRoute
        TopLevelDestination.Devices -> DevicesRoute
        TopLevelDestination.Wifi -> WifiRoute
        TopLevelDestination.Ssh -> SshRoute
    }

/** Switches tab saving the current tab's stack and restoring the destination tab's. */
fun NavController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Tabs become a tab switch; the other tools open on top of the origin tab. */
fun NavController.openTool(tool: NetrikTool, origin: TopLevelDestination, target: String? = null) {
    val tab = tool.tab
    when {
        // SSH with a target (quick action from a device): opens the tab and the form with the host filled in.
        tool == NetrikTool.Ssh && target != null -> {
            navigateToTab(TopLevelDestination.Ssh)
            navigate(SshFormRoute(target = target))
        }
        tab != null -> navigateToTab(tab)
        tool == NetrikTool.Oui -> navigate(OuiRoute(origin = origin, mac = target))
        tool == NetrikTool.Ping -> navigate(PingRoute(origin = origin, target = target))
        tool == NetrikTool.Traceroute -> navigate(TracerouteRoute(origin = origin, target = target))
        tool == NetrikTool.PortScanner -> navigate(PortScanRoute(origin = origin, target = target))
        tool == NetrikTool.PortKnock -> navigate(KnockRoute(origin = origin))
        tool == NetrikTool.Neighbors -> navigate(NeighborsRoute(origin = origin))
        tool == NetrikTool.SubnetCalculator -> navigate(SubnetRoute(origin = origin, target = target))
        else -> navigate(ToolRoute(tool = tool, origin = origin, target = target))
    }
}

/** Tab that should be highlighted for this back stack entry. */
fun NavBackStackEntry.topLevelDestination(): TopLevelDestination? {
    if (destination.hasRoute<ToolRoute>()) return toRoute<ToolRoute>().origin
    if (destination.hasRoute<OuiRoute>()) return toRoute<OuiRoute>().origin
    if (destination.hasRoute<PingRoute>()) return toRoute<PingRoute>().origin
    if (destination.hasRoute<TracerouteRoute>()) return toRoute<TracerouteRoute>().origin
    if (destination.hasRoute<PortScanRoute>()) return toRoute<PortScanRoute>().origin
    if (destination.hasRoute<KnockRoute>()) return toRoute<KnockRoute>().origin
    if (destination.hasRoute<NeighborsRoute>()) return toRoute<NeighborsRoute>().origin
    if (destination.hasRoute<SubnetRoute>()) return toRoute<SubnetRoute>().origin
    if (destination.hasRoute<KnockFormRoute>()) return toRoute<KnockFormRoute>().origin
    return TopLevelDestination.entries.firstOrNull { tab ->
        destination.hierarchy.any { it.hasRoute(tab.graphClass) }
    }
}
