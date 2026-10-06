package com.netrik.core.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.netrik.R
import kotlinx.coroutines.launch

/** Opens a link in the browser; without an app that handles it, a Snackbar says so. */
@Composable
fun rememberOpenUrl(): (String) -> Unit {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.settings_no_browser)
    return remember(context, snackbar, scope, noBrowser) {
        { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (_: ActivityNotFoundException) {
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(noBrowser)
                }
            }
        }
    }
}
