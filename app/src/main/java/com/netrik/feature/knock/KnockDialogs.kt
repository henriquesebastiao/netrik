package com.netrik.feature.knock

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.netrik.R
import com.netrik.core.knock.KnockGroup

@Composable
fun KnockDialogHost(dialog: KnockDialog?, groups: List<KnockGroup>, viewModel: KnockViewModel) {
    when (dialog) {
        null -> Unit
        is KnockDialog.GroupEdit -> GroupEditDialog(dialog, viewModel)
        is KnockDialog.GroupDelete -> KnockAlert(
            icon = R.drawable.ic_folder_delete,
            title = stringResource(R.string.ssh_group_delete_title, dialog.name),
            body = if (dialog.knockCount == 0) {
                stringResource(R.string.ssh_group_delete_empty)
            } else {
                pluralStringResource(R.plurals.knock_group_delete_body, dialog.knockCount, dialog.knockCount)
            },
            confirm = stringResource(R.string.action_delete),
            danger = true,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::confirmGroupDelete,
        )
        is KnockDialog.KnockDelete -> KnockAlert(
            icon = R.drawable.ic_delete,
            title = stringResource(R.string.ssh_host_delete_title, dialog.name),
            body = stringResource(R.string.knock_delete_body),
            confirm = stringResource(R.string.action_delete),
            danger = true,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::confirmKnockDelete,
        )
        is KnockDialog.Move -> MoveDialog(dialog, groups, viewModel)
        is KnockDialog.Imported -> {
            val result = dialog.result
            val lines = buildList {
                add(pluralStringResource(R.plurals.knock_import_added, result.imported, result.imported))
                if (result.groupsCreated > 0) add(pluralStringResource(R.plurals.knock_import_groups, result.groupsCreated, result.groupsCreated))
                if (result.duplicates > 0) add(pluralStringResource(R.plurals.knock_import_duplicates, result.duplicates, result.duplicates))
                if (result.invalid > 0) add(pluralStringResource(R.plurals.knock_import_invalid, result.invalid, result.invalid))
            }
            KnockAlert(
                icon = R.drawable.ic_download,
                title = stringResource(R.string.knock_import_done),
                body = lines.joinToString("\n"),
                confirm = null,
                dismiss = stringResource(R.string.action_close),
                onDismiss = viewModel::dismissDialog,
                onConfirm = {},
            )
        }
        is KnockDialog.ImportFailed -> KnockAlert(
            icon = R.drawable.ic_error_filled,
            iconTint = MaterialTheme.colorScheme.error,
            title = stringResource(R.string.knock_import_failed),
            body = stringResource(
                when (dialog.reason) {
                    ImportFailure.Unreadable -> R.string.knock_import_unreadable
                    ImportFailure.NotKnockFile -> R.string.knock_import_not_knock
                    ImportFailure.NewerVersion -> R.string.knock_import_newer
                    ImportFailure.Empty -> R.string.knock_import_empty
                },
            ),
            confirm = null,
            dismiss = stringResource(R.string.action_close),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {},
        )
    }
}

@Composable
private fun KnockAlert(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    confirm: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dismiss: String = stringResource(R.string.action_cancel),
    iconTint: Color = MaterialTheme.colorScheme.primary,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(icon), contentDescription = null, tint = iconTint) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            if (confirm != null) {
                TextButton(onClick = onConfirm) {
                    Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

@Composable
private fun GroupEditDialog(dialog: KnockDialog.GroupEdit, viewModel: KnockViewModel) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val canSave = dialog.value.isNotBlank()
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        icon = {
            Icon(
                painterResource(if (dialog.groupId == null) R.drawable.ic_create_new_folder else R.drawable.ic_drive_file_rename_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(if (dialog.groupId == null) R.string.ssh_group_dialog_new else R.string.ssh_group_dialog_rename)) },
        text = {
            OutlinedTextField(
                value = dialog.value,
                onValueChange = viewModel::onGroupNameChange,
                label = { Text(stringResource(R.string.ssh_group_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) viewModel.confirmGroupEdit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::confirmGroupEdit, enabled = canSave) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "Move to group": one choice per group plus "No group". */
@Composable
private fun MoveDialog(dialog: KnockDialog.Move, groups: List<KnockGroup>, viewModel: KnockViewModel) {
    val options = groups.map { it.id as Long? to it.name } + (null to stringResource(R.string.ssh_no_group))
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        icon = { Icon(painterResource(R.drawable.ic_drive_file_move), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.knock_move_title, dialog.name)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (id, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = id == dialog.groupId, role = Role.RadioButton, onClick = { viewModel.confirmMove(id) })
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = id == dialog.groupId, onClick = null)
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.action_cancel)) } },
    )
}
