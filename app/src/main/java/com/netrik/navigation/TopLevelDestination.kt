package com.netrik.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.netrik.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// Graphs of each tab. Each tab has its own stack, saved and restored when switching.
@Serializable data object ToolsGraph
@Serializable data object DevicesGraph
@Serializable data object WifiGraph
@Serializable data object SshGraph

/** The four navigation bar tabs (top-level destinations). */
enum class TopLevelDestination(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val graph: Any,
) {
    Tools(R.string.tab_tools, R.drawable.ic_home_repair_service, R.drawable.ic_home_repair_service_filled, ToolsGraph),
    Devices(R.string.tab_devices, R.drawable.ic_lan, R.drawable.ic_lan_filled, DevicesGraph),
    Wifi(R.string.tab_wifi, R.drawable.ic_wifi, R.drawable.ic_wifi_filled, WifiGraph),
    Ssh(R.string.tab_ssh, R.drawable.ic_terminal, R.drawable.ic_terminal_filled, SshGraph),
    ;

    val graphClass: KClass<*> get() = graph::class
}
