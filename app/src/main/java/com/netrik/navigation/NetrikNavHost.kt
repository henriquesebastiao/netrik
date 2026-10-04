package com.netrik.navigation

import androidx.compose.runtime.Composable
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
import com.netrik.feature.hub.HubScreen
import com.netrik.feature.oui.OuiScreen
import com.netrik.feature.ping.PingScreen
import com.netrik.feature.traceroute.TracerouteScreen
import com.netrik.feature.wifi.WifiScreen
import com.netrik.feature.placeholder.TabPlaceholderScreen
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
            composable<DevicesRoute> { TabPlaceholder(TopLevelDestination.Devices, navController) }
        }
        navigation<WifiGraph>(startDestination = WifiRoute) {
            composable<WifiRoute> { WifiScreen() }
        }
        navigation<SshGraph>(startDestination = SshRoute) {
            composable<SshRoute> { TabPlaceholder(TopLevelDestination.Ssh, navController) }
        }
        composable<OuiRoute> {
            OuiScreen(
                onBack = { navController.popBackStack() },
                onFindInNetwork = { navController.navigateToTab(TopLevelDestination.Devices) },
            )
        }
        composable<PingRoute> { PingScreen(onBack = { navController.popBackStack() }) }
        composable<TracerouteRoute> { TracerouteScreen(onBack = { navController.popBackStack() }) }
        composable<ToolRoute> { entry ->
            val route = entry.toRoute<ToolRoute>()
            ToolPlaceholderScreen(tool = route.tool, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun TabPlaceholder(tab: TopLevelDestination, navController: NavController) {
    TabPlaceholderScreen(tab = tab, onBackToTools = { navController.navigateToTab(TopLevelDestination.Tools) })
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
        tab != null -> navigateToTab(tab)
        tool == NetrikTool.Oui -> navigate(OuiRoute(origin = origin, mac = target))
        tool == NetrikTool.Ping -> navigate(PingRoute(origin = origin, target = target))
        tool == NetrikTool.Traceroute -> navigate(TracerouteRoute(origin = origin, target = target))
        else -> navigate(ToolRoute(tool = tool, origin = origin, target = target))
    }
}

/** Aba que deve aparecer destacada para esta entrada da pilha. */
fun NavBackStackEntry.topLevelDestination(): TopLevelDestination? {
    if (destination.hasRoute<ToolRoute>()) return toRoute<ToolRoute>().origin
    if (destination.hasRoute<OuiRoute>()) return toRoute<OuiRoute>().origin
    if (destination.hasRoute<PingRoute>()) return toRoute<PingRoute>().origin
    if (destination.hasRoute<TracerouteRoute>()) return toRoute<TracerouteRoute>().origin
    return TopLevelDestination.entries.firstOrNull { tab ->
        destination.hierarchy.any { it.hasRoute(tab.graphClass) }
    }
}
