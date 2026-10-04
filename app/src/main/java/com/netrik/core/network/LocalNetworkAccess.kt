package com.netrik.core.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Android 17+ local network permission (`ACCESS_LOCAL_NETWORK`, Nearby devices group). */
fun interface LocalNetworkAccess {
    fun isGranted(): Boolean

    companion object {
        const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

        /** The permission only exists from Android 17 (API 37) on; before that, access is free. */
        const val SINCE_API = 37

        fun isGranted(context: Context): Boolean =
            Build.VERSION.SDK_INT < SINCE_API ||
                ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}

class AndroidLocalNetworkAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocalNetworkAccess {
    override fun isGranted(): Boolean = LocalNetworkAccess.isGranted(context)
}
