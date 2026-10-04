package com.netrik.core.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.netrik.core.network.LocalNetworkAccess

/**
 * Asks for `ACCESS_LOCAL_NETWORK`; calls [onGranted] if granted. If the user already denied it for good,
 * the dialog no longer shows up and the request opens the app settings.
 */
@Composable
fun rememberLocalNetworkPermissionRequest(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> onGranted()
            activity?.shouldShowRequestPermissionRationale(LocalNetworkAccess.PERMISSION) == false ->
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }
    }
    return { launcher.launch(LocalNetworkAccess.PERMISSION) }
}
