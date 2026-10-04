package com.netrik.feature.placeholder

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.netrik.R
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.PlaceholderContent
import com.netrik.navigation.NetrikTool
import com.netrik.navigation.TopLevelDestination

// Telas provisórias da Etapa 0. Cada etapa substitui a sua pela tela real.

@Composable
fun TabPlaceholderScreen(tab: TopLevelDestination, onBackToTools: () -> Unit) {
    val title = stringResource(tab.title)
    Scaffold(topBar = { NetrikTopAppBar(title = title) }) { padding ->
        PlaceholderContent(
            icon = tab.icon,
            title = title,
            text = stringResource(R.string.placeholder_tab),
            actionLabel = stringResource(R.string.action_back_to_tools),
            onAction = onBackToTools,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun ToolPlaceholderScreen(tool: NetrikTool, onBack: () -> Unit) {
    val title = stringResource(tool.title)
    Scaffold(topBar = { NetrikTopAppBar(title = title, onBack = onBack) }) { padding ->
        PlaceholderContent(
            icon = tool.icon,
            title = title,
            text = stringResource(R.string.placeholder_tool),
            modifier = Modifier.padding(padding),
        )
    }
}
