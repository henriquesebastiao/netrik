package com.netrik.feature.knock

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.ToolEmptyState
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.knock.KnockProfile
import com.netrik.core.knock.summary
import com.netrik.core.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch

/** Port Knocking: saved sequences by group. Tapping a knock runs it. */
@Composable
fun KnockListScreen(
    viewModel: KnockViewModel,
    onBack: () -> Unit,
    onNewKnock: () -> Unit,
    onEditKnock: (Long) -> Unit,
) {
    val state by viewModel.list.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> launch { snackbar.showSnackbar(knockMessageText(context, message)) } }
    }

    // Group being exported (null = everything); kept across the file picker.
    var exportGroup by rememberSaveable { mutableStateOf<Long?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        uri?.let { viewModel.exportTo(it, exportGroup) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }
    val defaultFile = stringResource(R.string.knock_export_file)
    fun export(groupId: Long?, name: String?) {
        exportGroup = groupId
        exportLauncher.launch((if (name == null) defaultFile else "$defaultFile-${fileSafe(name)}") + ".json")
    }

    Scaffold(
        topBar = {
            NetrikTopAppBar(
                title = stringResource(R.string.tool_port_knock),
                onBack = onBack,
                actions = {
                    TopMenu(
                        anyExpanded = state.anyExpanded,
                        canExport = !state.isEmpty,
                        onNewGroup = viewModel::newGroup,
                        onSetAllExpanded = viewModel::setAllExpanded,
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        onExport = { export(null, null) },
                    )
                },
            )
        },
        floatingActionButton = {
            // The Snackbar lives in the app Scaffold, on top of this one: the FAB moves up while it shows.
            val lift by animateDpAsState(if (snackbar.currentSnackbarData != null) 64.dp else 0.dp, label = "fab")
            ExtendedFloatingActionButton(
                modifier = Modifier.padding(bottom = lift),
                onClick = onNewKnock,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.knock_new)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        ) {
            if (state.isEmpty) {
                item { ToolEmptyState(icon = R.drawable.ic_door_open, text = stringResource(R.string.knock_empty)) }
            }
            state.groups.forEach { group ->
                item(key = "g${group.id}") {
                    GroupHeader(
                        group = group,
                        onToggle = { viewModel.toggleGroup(group) },
                        onRename = { viewModel.renameGroup(group) },
                        onExport = { export(group.id, group.name) },
                        onDelete = { viewModel.deleteGroup(group) },
                    )
                }
                if (group.expanded) {
                    itemsIndexed(group.knocks, key = { _, knock -> "k${knock.id}" }) { index, knock ->
                        val copyName = stringResource(R.string.knock_copy_name, knock.name)
                        KnockRow(
                            knock = knock,
                            index = index,
                            count = group.knocks.size,
                            onClick = { viewModel.knock(knock) },
                            onEdit = { onEditKnock(knock.id) },
                            onDuplicate = { viewModel.duplicate(knock, copyName) },
                            onMove = { viewModel.moveKnock(knock) },
                            onDelete = { viewModel.deleteKnock(knock) },
                        )
                    }
                    if (group.knocks.isEmpty()) {
                        item(key = "e${group.id}") { EmptyGroup() }
                    }
                }
                item(key = "s${group.id}") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    KnockDialogHost(dialog, state.allGroups, viewModel)
    run?.let { KnockRunSheet(it, viewModel) }
}

private const val JSON_MIME = "application/json"

/** Group name usable in a file name. */
private fun fileSafe(name: String): String =
    name.trim().replace(Regex("""[^\p{L}\p{N}._-]+"""), "-").trim('-').ifEmpty { "group" }

@Composable
private fun TopMenu(
    anyExpanded: Boolean,
    canExport: Boolean,
    onNewGroup: () -> Unit,
    onSetAllExpanded: (Boolean) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(R.string.ssh_new_group, R.drawable.ic_create_new_folder) { open = false; onNewGroup() }
            MenuItem(
                if (anyExpanded) R.string.ssh_collapse_all else R.string.ssh_expand_all,
                if (anyExpanded) R.drawable.ic_unfold_less else R.drawable.ic_unfold_more,
            ) { open = false; onSetAllExpanded(!anyExpanded) }
            MenuItem(R.string.knock_import, R.drawable.ic_download) { open = false; onImport() }
            if (canExport) MenuItem(R.string.knock_export_all, R.drawable.ic_upload) { open = false; onExport() }
        }
    }
}

@Composable
private fun MenuItem(label: Int, icon: Int, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(stringResource(label), color = color) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}

@Composable
private fun GroupHeader(group: KnockGroupUi, onToggle: () -> Unit, onRename: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onToggle)
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
                    group.knocks.size.toString(),
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
                    Icon(painterResource(R.drawable.ic_more_horiz), contentDescription = stringResource(R.string.ssh_group_options), tint = colors.onSurfaceVariant)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    MenuItem(R.string.ssh_group_rename, R.drawable.ic_drive_file_rename_outline) { open = false; onRename() }
                    if (group.knocks.isNotEmpty()) MenuItem(R.string.knock_export_group, R.drawable.ic_upload) { open = false; onExport() }
                    MenuItem(R.string.ssh_group_delete, R.drawable.ic_delete, danger = true) { open = false; onDelete() }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KnockRow(
    knock: KnockProfile,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
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
                modifier = Modifier.heightIn(min = 72.dp).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconAvatar(icon = R.drawable.ic_door_open)
                Column(modifier = Modifier.weight(1f)) {
                    Text(knock.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        knock.host,
                        style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stepsSummary(knock),
                        style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = colors.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.knock_options), tint = colors.onSurfaceVariant)
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(R.string.knock_run, R.drawable.ic_play_arrow_filled) { menu = false; onClick() }
                MenuItem(R.string.ssh_host_edit, R.drawable.ic_edit) { menu = false; onEdit() }
                MenuItem(R.string.knock_duplicate, R.drawable.ic_content_copy) { menu = false; onDuplicate() }
                MenuItem(R.string.knock_move, R.drawable.ic_drive_file_move) { menu = false; onMove() }
                MenuItem(R.string.ssh_host_delete, R.drawable.ic_delete, danger = true) { menu = false; onDelete() }
            }
        }
    }
}

/** "TCP 7000 → UDP 8000 → ICMP 64 B". */
@Composable
fun stepsSummary(knock: KnockProfile): String {
    val resources = LocalResources.current
    return knock.steps.summary { size -> resources.getString(R.string.knock_icmp_size, size) }
}

@Composable
private fun EmptyGroup() {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.knock_group_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

fun knockMessageText(context: Context, message: KnockMessage): String = when (message) {
    is KnockMessage.GroupCreated -> context.getString(R.string.ssh_group_created, message.name)
    KnockMessage.GroupRenamed -> context.getString(R.string.ssh_group_renamed)
    KnockMessage.GroupDeleted -> context.getString(R.string.ssh_group_deleted)
    KnockMessage.KnockDeleted -> context.getString(R.string.knock_deleted)
    KnockMessage.KnockMoved -> context.getString(R.string.knock_moved)
    is KnockMessage.KnockDuplicated -> context.getString(R.string.knock_duplicated, message.name)
    KnockMessage.Exported -> context.getString(R.string.knock_exported)
    KnockMessage.ExportFailed -> context.getString(R.string.knock_export_failed)
}
