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

/** Estrutura do app: conteúdo das abas, barra de navegação e Snackbar acima dela. */
@Composable
fun NetrikApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.topLevelDestination() ?: TopLevelDestination.Tools

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            // imePadding: com o teclado aberto (terminal SSH) o aviso aparece acima dele.
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.imePadding()) },
            bottomBar = {
                // Formulário e terminal SSH ocupam a tela toda, como no protótipo.
                val fullScreen = backStackEntry?.destination?.let { it.hasRoute<SshFormRoute>() || it.hasRoute<SshTerminalRoute>() } == true
                if (!fullScreen) NetrikNavigationBar(
                    current = currentTab,
                    onSelect = { tab ->
                        if (tab == currentTab) {
                            // Tocar na aba atual volta à tela inicial dela.
                            navController.popBackStack(route = tab.startRoute, inclusive = false)
                        } else {
                            navController.navigateToTab(tab)
                        }
                    },
                )
            },
            // Cada tela cuida dos próprios insets superiores; a barra cuida dos inferiores.
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
