package com.netrik.core.ui

import android.content.ClipData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/** SnackbarHost único do app, acima da barra de navegação. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Copia um valor e mostra uma Snackbar dizendo o que foi copiado, como pede o design.
 * Mantida também no Android 13+, onde o aviso do sistema não informa qual valor foi copiado.
 */
fun interface CopyAction {
    fun copy(value: String, confirmation: String)
}

@Composable
fun rememberCopyAction(): CopyAction {
    val clipboard = LocalClipboard.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, snackbar, scope) {
        CopyAction { value, confirmation ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(confirmation, value)))
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(confirmation)
            }
        }
    }
}
