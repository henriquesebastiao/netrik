package com.netrik.feature.ssh

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.SearchTopBar
import com.netrik.core.designsystem.component.ToolEmptyState
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.ssh.SshAuth
import com.netrik.core.ssh.SshHost
import com.netrik.core.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch

@Composable
fun SshHostsScreen(
    viewModel: SshViewModel,
    onNewHost: () -> Unit,
    onEditHost: (hostId: Long, passwordRejected: Boolean) -> Unit,
    onOpenTerminal: () -> Unit,
) {
    val state by viewModel.list.collectAsStateWithLifecycle()
    val terminals by viewModel.terminals.collectAsStateWithLifecycle()
    val activeHosts = terminals.mapNotNull { it.hostId }.toSet()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    BackHandler(enabled = state.searchOpen, onBack = viewModel::closeSearch)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SshEvent.Message -> launch { snackbar.showSnackbar(sshMessageText(context, event.text)) }
                is SshEvent.EditHost -> onEditHost(event.hostId, event.passwordRejected)
                SshEvent.OpenTerminal -> onOpenTerminal()
                SshEvent.CloseForm, SshEvent.CloseTerminal -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            if (state.searchOpen) {
                SearchTopBar(
                    query = state.query,
                    hint = stringResource(R.string.ssh_search_hint),
                    closeLabel = stringResource(R.string.devices_search_close),
                    onQueryChange = viewModel::onQueryChange,
                    onClose = viewModel::closeSearch,
                    autoFocus = true,
                )
            } else {
                NetrikTopAppBar(
                    title = stringResource(R.string.tab_ssh),
                    actions = {
                        IconButton(onClick = viewModel::openSearch) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.ssh_search))
                        }
                        TopMenu(anyExpanded = state.anyExpanded, onNewGroup = viewModel::newGroup, onSetAllExpanded = viewModel::setAllExpanded)
                    },
                )
            }
        },
        floatingActionButton = {
            // O Snackbar fica no Scaffold do app, por cima deste: o FAB sobe enquanto ele aparece.
            val lift by animateDpAsState(if (snackbar.currentSnackbarData != null) 64.dp else 0.dp, label = "fab")
            ExtendedFloatingActionButton(
                modifier = Modifier.padding(bottom = lift),
                onClick = onNewHost,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.ssh_new_host)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        ) {
            if (terminals.isNotEmpty() && state.query.isBlank()) {
                item(key = "sessions") { ActiveSessionsBanner(terminals.size, terminals.joinToString(" · ") { it.name }, onOpenTerminal) }
            }
            if (state.isEmpty) {
                item { ToolEmptyState(icon = R.drawable.ic_terminal, text = stringResource(R.string.ssh_empty)) }
            }
            state.groups.forEach { group ->
                item(key = "g${group.id}") {
                    GroupHeader(
                        group = group,
                        searching = state.query.isNotBlank(),
                        onToggle = { viewModel.toggleGroup(group) },
                        onRename = { viewModel.renameGroup(group) },
                        onDelete = { viewModel.deleteGroup(group) },
                    )
                }
                if (group.expanded) {
                    itemsIndexed(group.hosts, key = { _, host -> "h${host.id}" }) { index, host ->
                        HostRow(
                            host = host,
                            index = index,
                            count = group.hosts.size,
                            sessionActive = host.id in activeHosts,
                            onClick = { viewModel.connectSaved(host.id) },
                            onEdit = { onEditHost(host.id, false) },
                            onDelete = { viewModel.deleteHost(host.id, host.name) },
                        )
                    }
                    if (group.hosts.isEmpty() && state.query.isBlank()) {
                        item(key = "e${group.id}") { EmptyGroup() }
                    }
                }
                item(key = "s${group.id}") { Spacer(Modifier.height(8.dp)) }
            }
            if (state.noMatch) {
                item { ToolEmptyState(icon = R.drawable.ic_search_off, text = stringResource(R.string.ssh_no_match, state.query.trim())) }
            }
        }
    }

    SshDialogHost(dialog, viewModel)
}

@Composable
private fun TopMenu(anyExpanded: Boolean, onNewGroup: () -> Unit, onSetAllExpanded: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ssh_new_group)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_create_new_folder), contentDescription = null) },
                onClick = {
                    open = false
                    onNewGroup()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (anyExpanded) R.string.ssh_collapse_all else R.string.ssh_expand_all)) },
                leadingIcon = {
                    Icon(painterResource(if (anyExpanded) R.drawable.ic_unfold_less else R.drawable.ic_unfold_more), contentDescription = null)
                },
                onClick = {
                    open = false
                    onSetAllExpanded(!anyExpanded)
                },
            )
        }
    }
}

@Composable
private fun GroupHeader(group: HostGroupUi, searching: Boolean, onToggle: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(enabled = !searching, onClick = onToggle)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(if (group.expanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                group.name ?: stringResource(R.string.ssh_no_group),
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceContainerHigh) {
                Text(
                    if (searching) "${group.hosts.size}/${group.total}" else group.total.toString(),
                    style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 11.sp, lineHeight = 20.sp),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
        if (group.id != null) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(
                        painterResource(R.drawable.ic_more_horiz),
                        contentDescription = stringResource(R.string.ssh_group_options),
                        tint = colors.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ssh_group_rename)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_drive_file_rename_outline), contentDescription = null) },
                        onClick = {
                            open = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ssh_group_delete), color = colors.error) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = colors.error) },
                        onClick = {
                            open = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostRow(host: SshHost, index: Int, count: Int, sessionActive: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(bottom = 2.dp)) {
        Surface(
            shape = groupedItemShape(index, count),
            color = colors.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(groupedItemShape(index, count))
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        ) {
            Row(
                modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box {
                    IconAvatar(icon = R.drawable.ic_dns)
                    if (sessionActive) {
                        // Ponto verde com contorno da cor do item, no canto do avatar.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(2.dp, 2.dp)
                                .size(14.dp)
                                .background(colors.surfaceContainer, CircleShape)
                                .padding(2.dp)
                                .background(NetrikTheme.extendedColors.success, CircleShape),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            host.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (sessionActive) {
                            Text(
                                stringResource(R.string.ssh_host_session_active),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NetrikTheme.extendedColors.success,
                            )
                        }
                    }
                    Text(
                        host.address,
                        style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AuthChip(host.auth)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ssh_host_edit)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                onClick = {
                    menu = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ssh_host_delete), color = colors.error) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = colors.error) },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun AuthChip(auth: SshAuth) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceContainerHighest, contentColor = colors.onSurfaceVariant) {
        Row(
            modifier = Modifier.height(24.dp).padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painterResource(if (auth == SshAuth.Key) R.drawable.ic_vpn_key else R.drawable.ic_password),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(if (auth == SshAuth.Key) R.string.ssh_auth_key_short else R.string.ssh_auth_password_short),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EmptyGroup() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.ssh_group_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Faixa "N sessões ativas" no topo da lista, com os nomes e "Abrir". */
@Composable
private fun ActiveSessionsBanner(count: Int, names: String, onOpen: () -> Unit) {
    val ext = NetrikTheme.extendedColors
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = ext.successContainer,
        contentColor = ext.onSuccessContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(painterResource(R.drawable.ic_terminal_filled), contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.ssh_sessions_active, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    names,
                    style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ssh_sessions_open), style = MaterialTheme.typography.labelLarge)
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}
