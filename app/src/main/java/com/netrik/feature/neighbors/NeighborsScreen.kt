package com.netrik.feature.neighbors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.ErrorCard
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.RunButton
import com.netrik.core.designsystem.component.RunHeader
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.component.StatusTone
import com.netrik.core.designsystem.component.ToolEmptyState
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.neighbor.Mndp
import com.netrik.core.neighbor.Neighbor
import com.netrik.core.neighbor.NeighborProtocol
import com.netrik.core.neighbor.UbiquitiDiscovery
import com.netrik.core.ui.RelativeTime
import com.netrik.core.ui.rememberLocalNetworkPermissionRequest
import com.netrik.core.ui.relativeTimeText
import com.netrik.navigation.NetrikTool
import java.time.ZoneId

/** MikroTik (MNDP) and Ubiquiti neighbor discovery. Not in the design: built from the design system tool components. */
@Composable
fun NeighborsScreen(
    onBack: () -> Unit,
    onAction: (NetrikTool, String) -> Unit,
    viewModel: NeighborsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestLocalNetwork = rememberLocalNetworkPermissionRequest(onGranted = viewModel::start)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    // Only sends broadcasts while the screen is visible.
    LifecycleStartEffect(viewModel) {
        viewModel.resume()
        onStopOrDispose { viewModel.pause() }
    }

    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tool_neighbors),
                onBack = onBack,
                actions = {
                    if (state.neighbors.isNotEmpty()) {
                        IconButton(onClick = viewModel::clear) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.neighbors_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val mikrotikTitle = stringResource(R.string.neighbors_section_mikrotik)
        val ubiquitiTitle = stringResource(R.string.neighbors_section_ubiquiti)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                RunHeader(
                    chipLabel = stringResource(
                        when {
                            state.running -> R.string.neighbors_listening
                            else -> R.string.neighbors_stopped
                        },
                    ),
                    chipTone = StatusTone.Neutral,
                    running = state.running,
                    runLine = "MNDP UDP ${Mndp.PORT} · Ubiquiti UDP ${UbiquitiDiscovery.PORT}",
                    onCopy = null,
                )
            }
            if (state.needsPermission) {
                item(key = "permission") {
                    ErrorCard(
                        stringResource(R.string.error_local_network_title),
                        stringResource(R.string.error_local_network_body),
                        onRetry = requestLocalNetwork,
                        retryLabel = stringResource(R.string.action_allow),
                        retryIcon = R.drawable.ic_lan,
                    )
                }
            }
            if (!state.onLocalNetwork) {
                item(key = "lan") { Notice(R.drawable.ic_signal_disconnected, stringResource(R.string.neighbors_no_lan)) }
            }
            if (state.mndpPortBusy) {
                item(key = "port") { Notice(R.drawable.ic_warning_filled, stringResource(R.string.neighbors_port_busy, Mndp.PORT)) }
            }
            item(key = "run") { RunButton(running = state.running, enabled = true, onStart = viewModel::start, onStop = viewModel::stop) }

            if (state.neighbors.isEmpty()) {
                item(key = "empty") {
                    ToolEmptyState(
                        icon = R.drawable.ic_router,
                        text = stringResource(if (state.running) R.string.neighbors_searching else R.string.neighbors_none),
                    )
                }
            }
            section("mikrotik", mikrotikTitle, state.mikrotik, state.nowMillis) { selected = it.key }
            section("ubiquiti", ubiquitiTitle, state.ubiquiti, state.nowMillis) { selected = it.key }
            item(key = "note") {
                Text(
                    stringResource(R.string.neighbors_l2_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    state.neighbors.firstOrNull { it.key == selected }?.let { neighbor ->
        NeighborSheet(
            neighbor = neighbor,
            nowMillis = state.nowMillis,
            onDismiss = { selected = null },
            onAction = { tool, target ->
                selected = null
                onAction(tool, target)
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    key: String,
    title: String,
    neighbors: List<Neighbor>,
    nowMillis: Long,
    onClick: (Neighbor) -> Unit,
) {
    if (neighbors.isEmpty()) return
    item(key = "h-$key") { SectionHeader("$title · ${neighbors.size}") }
    item(key = "l-$key") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            neighbors.forEachIndexed { index, neighbor ->
                NeighborRow(neighbor, index, neighbors.size, nowMillis) { onClick(neighbor) }
            }
        }
    }
}

@Composable
private fun NeighborRow(neighbor: Neighbor, index: Int, count: Int, nowMillis: Long, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = groupedItemShape(index, count), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconAvatar(icon = R.drawable.ic_router)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(neighbor.title(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(neighbor.address, neighbor.mac).joinToString(" · "),
                    style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                neighbor.summary()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(seenText(neighbor.lastSeenMillis, nowMillis), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Notice(icon: Int, text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Identity/hostname; without it, the model or the address. */
@Composable
internal fun Neighbor.title(): String = identity ?: model ?: address

/** "hAP ax² · RouterOS 7.14.3 (stable)" / "UAP-AC-Lite · v6.5.62"; the full version string is in the details. */
@Composable
private fun Neighbor.summary(): String? {
    val version = version?.let { if (protocol == NeighborProtocol.Mndp) stringResource(R.string.neighbors_routeros, shortVersion(it)) else it }
    return listOfNotNull(model, version).joinToString(" · ").ifEmpty { null }
}

/** "5 s ago" under a minute, then the app's relative time ("2 min ago"). */
@Composable
internal fun seenText(thenMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - thenMillis) / 1_000).coerceAtLeast(0)
    return if (seconds < 60) {
        stringResource(R.string.neighbors_seen_seconds, seconds.toInt())
    } else {
        relativeTimeText(RelativeTime.format(thenMillis, nowMillis, ZoneId.systemDefault()))
    }
}

/** RouterOS sends "7.14.3 (stable) Apr/17/2024 08:12:22": the list keeps the version and channel only. */
internal fun shortVersion(version: String): String = version.split(' ').filter { it.isNotBlank() }.take(2).joinToString(" ")
