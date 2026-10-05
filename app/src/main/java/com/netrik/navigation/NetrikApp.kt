package com.netrik.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netrik.core.ui.LocalSnackbarHostState

/** App structure: tab content, navigation bar and the Snackbar above it. */
@Composable
fun NetrikApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.topLevelDestination() ?: TopLevelDestination.Tools

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            // imePadding: with the keyboard open (SSH terminal) the notice shows above it.
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.imePadding()) },
            bottomBar = {
                // The SSH form and terminal take the whole screen, as in the prototype; the knock form follows the SSH form.
                val fullScreen = backStackEntry?.destination?.let {
                    it.hasRoute<SshFormRoute>() || it.hasRoute<SshTerminalRoute>() || it.hasRoute<KnockFormRoute>()
                } == true
                if (!fullScreen) NetrikNavigationBar(
                    current = currentTab,
                    onSelect = { tab ->
                        if (tab == currentTab) {
                            // Tapping the current tab goes back to its start screen.
                            navController.popBackStack(route = tab.startRoute, inclusive = false)
                        } else {
                            navController.navigateToTab(tab)
                        }
                    },
                )
            },
            // Each screen handles its own top insets; the bar handles the bottom ones.
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            NetrikNavHost(
                navController = navController,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            )
        }
    }
}

@Composable
private fun NetrikNavigationBar(current: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.title),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }
    }
}
