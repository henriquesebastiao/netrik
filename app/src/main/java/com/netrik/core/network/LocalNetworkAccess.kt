package com.netrik.core.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Permissão de rede local do Android 17+ (`ACCESS_LOCAL_NETWORK`, grupo Dispositivos próximos). */
fun interface LocalNetworkAccess {
    fun isGranted(): Boolean

    companion object {
        const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

        /** A permissão só existe a partir do Android 17 (API 37); antes disso o acesso é livre. */
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
