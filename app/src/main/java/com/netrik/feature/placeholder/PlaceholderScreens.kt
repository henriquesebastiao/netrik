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

// Tela provisória para ferramentas do catálogo ainda sem rota própria (ver ToolRoute).

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
