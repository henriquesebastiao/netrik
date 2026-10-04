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

/** The app's single SnackbarHost, above the navigation bar. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Copies a value and shows a Snackbar saying what was copied, as the design asks.
 * Kept on Android 13+ too, where the system notice doesn't say which value was copied.
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
