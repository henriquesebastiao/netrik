package com.netrik.feature.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.BuildConfig
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
    viewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HubContent(state = state, onOpenTool = onOpenTool, onShowPublicIp = viewModel::onShowPublicIp)
}

@Composable
fun HubContent(
    state: HubUiState,
    onOpenTool: (NetrikTool) -> Unit,
    onShowPublicIp: () -> Unit,
) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tab_tools),
                actions = { HubOverflowMenu(onAbout = { showAbout = true }) },
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
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
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

@Composable
private fun HubOverflowMenu(onAbout: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_about)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                onClick = {
                    expanded = false
                    onAbout()
                },
            )
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                Text(
                    text = stringResource(R.string.about_licenses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Preview(showBackground = true, heightDp = 915, widthDp = 412)
@Composable
private fun HubPreview() {
    NetrikTheme(dynamicColor = false) {
        HubContent(
            state = HubUiState(
                network = NetworkCardState.Connected(
                    transport = CurrentNetwork.Transport.Wifi,
                    name = "Escritório-5G",
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
