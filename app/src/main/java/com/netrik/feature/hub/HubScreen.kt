package com.netrik.feature.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.WifiBand
import com.netrik.navigation.NetrikTool
import com.netrik.navigation.ToolGroup

@Composable
fun HubScreen(
    onOpenTool: (NetrikTool) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HubContent(state = state, onOpenTool = onOpenTool, onShowPublicIp = viewModel::onShowPublicIp, onOpenSettings = onOpenSettings)
}

@Composable
fun HubContent(
    state: HubUiState,
    onOpenTool: (NetrikTool) -> Unit,
    onShowPublicIp: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tab_tools),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "network") {
                NetworkCard(network = state.network, publicIp = state.publicIp, onShowPublicIp = onShowPublicIp)
            }
            items(ToolGroup.entries, key = { it.name }) { group ->
                ToolGroupSection(group = group, onOpenTool = onOpenTool)
            }
        }
    }
}

@Composable
private fun ToolGroupSection(group: ToolGroup, onOpenTool: (NetrikTool) -> Unit) {
    val tools = NetrikTool.entries.filter { it.group == group }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(group.title))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            tools.forEachIndexed { index, tool ->
                ToolListItem(
                    tool = tool,
                    shapeIndex = index,
                    shapeCount = tools.size,
                    onClick = { onOpenTool(tool) },
                )
            }
        }
    }
}

@Composable
private fun ToolListItem(tool: NetrikTool, shapeIndex: Int, shapeCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = groupedItemShape(shapeIndex, shapeCount),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconAvatar(icon = tool.icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(tool.title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(tool.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(if (tool.tab != null) R.drawable.ic_arrow_outward else R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 915, widthDp = 412)
@Composable
private fun HubPreview() {
    NetrikTheme(dynamicColor = false) {
        HubContent(
            state = HubUiState(
                network = NetworkCardState.Connected(
                    transport = CurrentNetwork.Transport.Wifi,
                    name = "Office-5G",
                    hasInternet = true,
                    signal = WifiSignal(-54, WifiBand.GHz5, 36),
                    localIp = "192.168.0.42",
                    maskCidr = "255.255.255.0 /24",
                    gateway = "192.168.0.1",
                    dns = "1.1.1.1, 8.8.8.8",
                    ipv6 = "2804:14d:5c83:8a10::1f3a",
                    networkKey = "preview",
                ),
            ),
            onOpenTool = {},
            onShowPublicIp = {},
        )
    }
}
