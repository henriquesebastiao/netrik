package com.netrik.feature.ssh

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.R
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.terminal.ExtraKey
import com.netrik.core.terminal.SshTerminal
import com.netrik.core.terminal.StickyModifiers
import com.netrik.core.terminal.TerminalCanvasView
import com.netrik.core.terminal.TerminalFont
import com.netrik.core.terminal.TerminalInput
import com.netrik.core.ui.LocalSnackbarHostState
import com.netrik.core.ui.rememberCopyAction
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/** Cores fixas do terminal no design (escuras nos dois temas). */
private object TermColors {
    val Bg = Color(0xFF0A1112)
    val Bar = Color(0xFF131B1C)
    val Fg = Color(0xFFD7E0E2)
    val Muted = Color(0xFF8E9B9D)
    val Primary = Color(0xFF72D0D0)
    val Green = Color(0xFF7CD591)
    val Yellow = Color(0xFFEBC573)
    val Red = Color(0xFFF98F87)
    val Line = Color(0xFF273031)
    val Chip = Color(0xFF1F282A)
    val ChipOn = Color(0xFF264344)
}

/** Abaixo desta altura de tela (dp), com o teclado aberto, o terminal esconde barra e abas. */
private const val COMPACT_HEIGHT_DP = 480

/** Pedido de notificação feito uma vez por execução do app (a notificação do serviço de sessões). */
private var notificationPermissionAsked = false

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SshTerminalScreen(viewModel: SshViewModel, onBack: () -> Unit) {
    val terminals by viewModel.terminals.collectAsStateWithLifecycle()
    val activeId by viewModel.activeTerminalId.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val active = terminals.firstOrNull { it.id == activeId } ?: terminals.firstOrNull()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val back by rememberUpdatedState(onBack)
    BackHandler(onBack = onBack)

    LaunchedEffect(viewModel) {
        // Para de ouvir ao sair: o aviso seguinte ("Desconectado de…") fica para a lista de hosts.
        viewModel.events.takeWhile { it != SshEvent.CloseTerminal }.collect { event ->
            if (event is SshEvent.Message) launch { snackbar.showSnackbar(sshMessageText(context, event.text)) }
        }
        back()
    }
    LaunchedEffect(terminals.isEmpty()) { if (terminals.isEmpty()) back() }

    DarkStatusBar()
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { keyboard?.hide(); canvasHideKeyboard(context) } }
    RequestNotificationPermissionOnce()

    var sticky by remember { mutableStateOf(StickyModifiers()) }
    var canvas by remember { mutableStateOf<TerminalCanvasView?>(null) }
    val padH = with(LocalDensity.current) { 12.dp.roundToPx() }
    val padV = with(LocalDensity.current) { 10.dp.roundToPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermColors.Bg)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Celular deitado com teclado aberto: sobra pouca altura, então barra e abas saem até o teclado fechar.
        val compact = WindowInsets.isImeVisible && LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
        Column(modifier = Modifier.background(TermColors.Bar).statusBarsPadding()) {
            if (!compact) {
                TerminalTopBar(active, fontSize, viewModel, onBack)
                SessionTabs(terminals, active?.id, viewModel::selectTerminal, viewModel::closeTerminal, onNewSession = onBack)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    TerminalCanvasView(ctx, stickyModifiers = { sticky }, onModifiersConsumed = { sticky = StickyModifiers() }).apply {
                        setPadding(padH, padV, padH, padV)
                        canvas = this
                    }
                },
                update = { view ->
                    view.terminal = active
                    view.setFontSize(fontSize)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = context.getString(R.string.ssh_term_screen, active?.name.orEmpty()) },
            )
            if (active != null) {
                val status by active.status.collectAsStateWithLifecycle()
                if (status == SshTerminal.Status.Connecting) ConnectingLine()
            }
        }
        ExtraKeysBar(sticky, singleRow = compact) { key ->
            if (key.isModifier) {
                sticky = sticky.toggle(key)
            } else {
                val terminal = active ?: return@ExtraKeysBar
                TerminalInput.encodeExtraKey(key, sticky, terminal.emulator.isCursorKeysApplicationMode)?.let(terminal::send)
                sticky = StickyModifiers()
            }
        }
    }

    // Redesenha a cada saída do servidor e abre o teclado ao entrar numa sessão.
    LaunchedEffect(active, canvas) {
        val view = canvas ?: return@LaunchedEffect
        val terminal = active ?: return@LaunchedEffect
        view.showKeyboard()
        terminal.revision.collect { view.onOutput() }
    }
}

@Composable
private fun TerminalTopBar(active: SshTerminal?, fontSize: Int, viewModel: SshViewModel, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.ssh_term_hosts), tint = TermColors.Fg)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                active?.name.orEmpty(),
                color = TermColors.Fg,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (active != null) {
                val status by active.status.collectAsStateWithLifecycle()
                val connected = status == SshTerminal.Status.Connected
                val dot = if (connected) TermColors.Green else TermColors.Yellow
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(dot, CircleShape))
                    Text(
                        stringResource(if (connected) R.string.ssh_term_connected else R.string.ssh_term_connecting),
                        color = dot,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        active.address,
                        color = TermColors.Muted,
                        style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        TerminalMenu(active, fontSize, viewModel)
    }
}

@Composable
private fun TerminalMenu(active: SshTerminal?, fontSize: Int, viewModel: SshViewModel) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val copy = rememberCopyAction()
    val copied = stringResource(R.string.ssh_term_output_copied)
    val itemColors = MenuDefaults.itemColors(textColor = TermColors.Fg, leadingIconColor = TermColors.Muted)
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more), tint = TermColors.Fg)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = TermColors.Chip, modifier = Modifier.width(236.dp)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ssh_term_copy_output)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null) },
                colors = itemColors,
                enabled = active != null,
                onClick = {
                    open = false
                    active?.let { copy.copy(it.transcript(), copied) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ssh_term_paste)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_content_paste), contentDescription = null) },
                colors = itemColors,
                enabled = active != null,
                onClick = {
                    open = false
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!text.isNullOrEmpty()) active?.paste(text)
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_format_size), contentDescription = null, tint = TermColors.Muted)
                Text(
                    stringResource(R.string.ssh_term_font),
                    color = TermColors.Fg,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                IconButton(onClick = { viewModel.changeFontSize(-1) }, enabled = fontSize > TerminalFont.MIN, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.ssh_term_font_down), tint = TermColors.Fg)
                }
                Text(
                    fontSize.toString(),
                    color = TermColors.Fg,
                    style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 13.sp),
                    modifier = Modifier.width(24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { viewModel.changeFontSize(1) }, enabled = fontSize < TerminalFont.MAX, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.ssh_term_font_up), tint = TermColors.Fg)
                }
            }
            HorizontalDivider(color = TermColors.Line, modifier = Modifier.padding(vertical = 8.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ssh_term_disconnect)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_link_off), contentDescription = null) },
                colors = MenuDefaults.itemColors(textColor = TermColors.Red, leadingIconColor = TermColors.Red),
                enabled = active != null,
                onClick = {
                    open = false
                    viewModel.disconnectActive()
                },
            )
        }
    }
}

@Composable
private fun SessionTabs(
    terminals: List<SshTerminal>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewSession: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(terminals, key = { it.id }) { terminal ->
            val selected = terminal.id == activeId
            val status by terminal.status.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) TermColors.ChipOn else TermColors.Chip),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.height(36.dp).clickable { onSelect(terminal.id) }.padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (status == SshTerminal.Status.Connected) TermColors.Green else TermColors.Yellow, CircleShape),
                    )
                    Text(
                        terminal.name,
                        color = if (selected) TermColors.Fg else TermColors.Muted,
                        style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box(
                    modifier = Modifier.size(width = 32.dp, height = 36.dp).clickable { onClose(terminal.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.ssh_term_close_session),
                        tint = TermColors.Muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        item(key = "new") {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, TermColors.Line), RoundedCornerShape(8.dp))
                    .clickable(onClick = onNewSession),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.ssh_term_new_session), tint = TermColors.Muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ConnectingLine() {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = TermColors.Yellow, strokeWidth = 2.dp)
        Text(stringResource(R.string.ssh_term_negotiating), color = TermColors.Muted, style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 13.sp))
    }
}

/**
 * Barra de teclas extras (6 colunas × 2 linhas, ou uma linha só no modo compacto); Ctrl e Alt ficam
 * destacados enquanto presos.
 */
@Composable
private fun ExtraKeysBar(sticky: StickyModifiers, singleRow: Boolean, onKey: (ExtraKey) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(TermColors.Bar)) {
        HorizontalDivider(color = TermColors.Line)
        ExtraKey.entries.chunked(if (singleRow) ExtraKey.entries.size else 6).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val on = (key == ExtraKey.Ctrl && sticky.ctrl) || (key == ExtraKey.Alt && sticky.alt)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(1.dp)
                            .height(if (singleRow) 36.dp else 46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) TermColors.Primary else Color.Transparent)
                            .clickable { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val icon = arrowIcon(key)
                        if (icon != null) {
                            Icon(painterResource(icon.first), contentDescription = stringResource(icon.second), tint = TermColors.Fg, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                key.label,
                                color = if (on) TermColors.Bg else TermColors.Fg,
                                style = NetrikTheme.dataTypography.dataSmall.copy(fontSize = 14.sp),
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

private fun arrowIcon(key: ExtraKey): Pair<Int, Int>? = when (key) {
    ExtraKey.Up -> R.drawable.ic_keyboard_arrow_up to R.string.ssh_key_up
    ExtraKey.Down -> R.drawable.ic_keyboard_arrow_down to R.string.ssh_key_down
    ExtraKey.Left -> R.drawable.ic_keyboard_arrow_left to R.string.ssh_key_left
    ExtraKey.Right -> R.drawable.ic_keyboard_arrow_right to R.string.ssh_key_right
    else -> null
}

/** Ícones claros na barra de status enquanto o terminal (escuro) está na tela. */
@Composable
private fun DarkStatusBar() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

/** Android 13+: pede a permissão da notificação "sessões SSH ativas" uma vez por execução. */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !notificationPermissionAsked) {
            notificationPermissionAsked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** O teclado foi aberto pela View do terminal (fora do Compose): fecha pelo InputMethodManager. */
private fun canvasHideKeyboard(context: android.content.Context) {
    val window = (context as? Activity)?.window ?: return
    context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
}
