package com.netrik.feature.ssh

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.netrik.R
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.ssh.HostKeys
import com.netrik.core.ssh.SshAuth
import com.netrik.core.ssh.SshFailure
import com.netrik.core.ui.rememberLocalNetworkPermissionRequest

/** Diálogos da aba SSH, comuns à lista de hosts e ao formulário. */
@Composable
fun SshDialogHost(dialog: SshDialog?, viewModel: SshViewModel) {
    val requestLocalNetwork = rememberLocalNetworkPermissionRequest(onGranted = viewModel::retry)
    when (dialog) {
        null -> Unit
        is SshDialog.GroupEdit -> GroupEditDialog(dialog, viewModel)
        is SshDialog.GroupDelete -> SshAlert(
            icon = R.drawable.ic_folder_delete,
            title = stringResource(R.string.ssh_group_delete_title, dialog.name),
            body = when (dialog.hostCount) {
                0 -> stringResource(R.string.ssh_group_delete_empty)
                1 -> stringResource(R.string.ssh_group_delete_one)
                else -> stringResource(R.string.ssh_group_delete_many, dialog.hostCount)
            },
            dismiss = stringResource(R.string.action_cancel),
            confirm = stringResource(R.string.action_delete),
            danger = true,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::confirmGroupDelete,
        )
        is SshDialog.HostDelete -> SshAlert(
            icon = R.drawable.ic_delete,
            title = stringResource(R.string.ssh_host_delete_title, dialog.name),
            body = stringResource(R.string.ssh_host_delete_body),
            dismiss = stringResource(R.string.action_cancel),
            confirm = stringResource(R.string.action_delete),
            danger = true,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::confirmHostDelete,
        )
        is SshDialog.Connecting -> ConnectingDialog(dialog.address, viewModel::cancelConnect)
        is SshDialog.Fingerprint -> SshAlert(
            icon = R.drawable.ic_vpn_key,
            title = stringResource(R.string.ssh_fp_title),
            body = stringResource(R.string.ssh_fp_body, dialog.host),
            mono = listOf(
                stringResource(R.string.ssh_fp_type) to HostKeys.displayType(dialog.key.type),
                stringResource(R.string.ssh_fp_fingerprint) to dialog.key.fingerprint,
            ),
            dismiss = stringResource(R.string.action_cancel),
            confirm = stringResource(R.string.ssh_fp_trust),
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::trustAndConnect,
        )
        is SshDialog.ChangedKey -> SshAlert(
            icon = R.drawable.ic_gpp_maybe,
            iconTint = MaterialTheme.colorScheme.error,
            title = stringResource(R.string.ssh_changed_title),
            body = stringResource(R.string.ssh_changed_body, dialog.host),
            mono = listOf(
                stringResource(R.string.ssh_fp_type) to HostKeys.displayType(dialog.presented.type),
                stringResource(R.string.ssh_changed_old) to dialog.stored.fingerprint,
                stringResource(R.string.ssh_changed_new) to dialog.presented.fingerprint,
            ),
            dismiss = stringResource(R.string.action_cancel),
            confirm = stringResource(R.string.ssh_changed_replace),
            danger = true,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::trustAndConnect,
        )
        is SshDialog.Failure -> FailureDialog(dialog, viewModel, requestLocalNetwork)
    }
}

@Composable
private fun FailureDialog(dialog: SshDialog.Failure, viewModel: SshViewModel, requestLocalNetwork: () -> Unit) {
    val who = "${dialog.user}@${dialog.host}"
    val close = stringResource(R.string.action_close)
    val retry = stringResource(R.string.action_retry)
    val edit = stringResource(R.string.ssh_auth_edit)
    when (val failure = dialog.failure) {
        SshFailure.Timeout -> SshAlert(
            R.drawable.ic_cloud_off, stringResource(R.string.ssh_unreach_title), stringResource(R.string.ssh_unreach_body, dialog.host, dialog.port),
            close, retry, onDismiss = viewModel::dismissDialog, onConfirm = viewModel::retry, iconTint = MaterialTheme.colorScheme.error,
        )
        SshFailure.Refused -> SshAlert(
            R.drawable.ic_cloud_off, stringResource(R.string.ssh_refused_title), stringResource(R.string.ssh_refused_body, dialog.host, dialog.port),
            close, retry, onDismiss = viewModel::dismissDialog, onConfirm = viewModel::retry, iconTint = MaterialTheme.colorScheme.error,
        )
        SshFailure.NoRoute -> SshAlert(
            R.drawable.ic_cloud_off, stringResource(R.string.ssh_unreach_title), stringResource(R.string.ssh_noroute_body, dialog.host),
            close, retry, onDismiss = viewModel::dismissDialog, onConfirm = viewModel::retry, iconTint = MaterialTheme.colorScheme.error,
        )
        SshFailure.HostNotFound -> SshAlert(
            R.drawable.ic_cloud_off, stringResource(R.string.ssh_notfound_title), stringResource(R.string.ssh_notfound_body, dialog.host),
            close, retry, onDismiss = viewModel::dismissDialog, onConfirm = viewModel::retry, iconTint = MaterialTheme.colorScheme.error,
        )
        SshFailure.AuthRejected -> SshAlert(
            R.drawable.ic_key_off, stringResource(R.string.ssh_auth_title),
            stringResource(if (dialog.auth == SshAuth.Password) R.string.ssh_auth_password_body else R.string.ssh_auth_key_body, who),
            close, edit, onDismiss = viewModel::dismissDialog, onConfirm = viewModel::editAfterAuthFailure, iconTint = MaterialTheme.colorScheme.error,
        )
        SshFailure.KeyInvalid -> SshAlert(
            R.drawable.ic_key_off, stringResource(R.string.ssh_key_invalid_title), stringResource(R.string.ssh_key_invalid_body),
            close, edit, onDismiss = viewModel::dismissDialog, onConfirm = viewModel::editAfterAuthFailure, iconTint = MaterialTheme.colorScheme.error,
        )
        SshFailure.KeyPassphraseRequired, SshFailure.KeyWrongPassphrase -> SshAlert(
            R.drawable.ic_key_off, stringResource(R.string.ssh_auth_title),
            stringResource(if (failure == SshFailure.KeyWrongPassphrase) R.string.ssh_form_key_passphrase_wrong else R.string.ssh_form_key_passphrase_required),
            close, edit, onDismiss = viewModel::dismissDialog, onConfirm = viewModel::editAfterAuthFailure, iconTint = MaterialTheme.colorScheme.error,
        )
        SshFailure.LocalNetworkPermission -> SshAlert(
            R.drawable.ic_lan, stringResource(R.string.error_local_network_title), stringResource(R.string.error_local_network_body),
            stringResource(R.string.action_cancel), stringResource(R.string.action_allow),
            onDismiss = viewModel::dismissDialog, onConfirm = requestLocalNetwork,
        )
        is SshFailure.NoCommonAlgorithm -> SshAlert(
            R.drawable.ic_error_filled, stringResource(R.string.ssh_algo_title),
            stringResource(R.string.ssh_algo_body, failure.detail ?: stringResource(R.string.ssh_unknown_error)),
            close, null, onDismiss = viewModel::dismissDialog, onConfirm = {}, iconTint = MaterialTheme.colorScheme.error,
        )
        is SshFailure.Other -> SshAlert(
            R.drawable.ic_error_filled, stringResource(R.string.ssh_other_title),
            stringResource(R.string.ssh_other_body, failure.detail ?: stringResource(R.string.ssh_unknown_error)),
            close, retry, onDismiss = viewModel::dismissDialog, onConfirm = viewModel::retry, iconTint = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Diálogo no estilo do protótipo: ícone, título centralizado, texto, bloco opcional de dados
 * em fonte monoespaçada e botões de texto. [confirm] nulo mostra só o botão de fechar.
 */
@Composable
private fun SshAlert(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    dismiss: String,
    confirm: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    mono: List<Pair<String, String>> = emptyList(),
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(icon), contentDescription = null, tint = iconTint) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(body)
                if (mono.isNotEmpty()) MonoBlock(mono)
            }
        },
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
private fun MonoBlock(rows: List<Pair<String, String>>) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.66.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        value,
                        style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectingDialog(address: String, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.ssh_connecting, address), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun GroupEditDialog(dialog: SshDialog.GroupEdit, viewModel: SshViewModel) {
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

/** Texto dos avisos (Snackbar) da aba SSH. */
fun sshMessageText(context: Context, message: SshMessage): String = when (message) {
    is SshMessage.Authenticated -> context.getString(R.string.ssh_authenticated, message.who)
    is SshMessage.GroupCreated -> context.getString(R.string.ssh_group_created, message.name)
    SshMessage.GroupRenamed -> context.getString(R.string.ssh_group_renamed)
    SshMessage.GroupDeleted -> context.getString(R.string.ssh_group_deleted)
    SshMessage.HostDeleted -> context.getString(R.string.ssh_host_deleted)
    is SshMessage.Retrying -> context.getString(R.string.ssh_retrying, message.host)
}
