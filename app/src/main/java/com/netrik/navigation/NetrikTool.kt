package com.netrik.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.netrik.R

enum class ToolGroup(@param:StringRes val title: Int) {
    Diagnostics(R.string.group_diagnostics),
    Discovery(R.string.group_discovery),
    RemoteAccess(R.string.group_remote_access),
}

/**
 * Hub tool catalog, in display order. To add a tool,
 * add an entry here and register its route in [NetrikNavHost].
 *
 * [tab] != null: the tool is a navigation bar tab (↗ shortcut in the hub).
 */
enum class NetrikTool(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:DrawableRes val icon: Int,
    val group: ToolGroup,
    val tab: TopLevelDestination? = null,
) {
    Ping(R.string.tool_ping, R.string.tool_ping_desc, R.drawable.ic_network_ping, ToolGroup.Diagnostics),
    Traceroute(R.string.tool_traceroute, R.string.tool_traceroute_desc, R.drawable.ic_route, ToolGroup.Diagnostics),
    Devices(R.string.tab_devices, R.string.tool_devices_desc, R.drawable.ic_lan, ToolGroup.Discovery, TopLevelDestination.Devices),
    PortScanner(R.string.tool_port_scanner, R.string.tool_port_scanner_desc, R.drawable.ic_radar, ToolGroup.Discovery),
    Wifi(R.string.tab_wifi, R.string.tool_wifi_desc, R.drawable.ic_wifi, ToolGroup.Discovery, TopLevelDestination.Wifi),
    Oui(R.string.tool_oui, R.string.tool_oui_desc, R.drawable.ic_manage_search, ToolGroup.Discovery),
    Ssh(R.string.tab_ssh, R.string.tool_ssh_desc, R.drawable.ic_terminal, ToolGroup.RemoteAccess, TopLevelDestination.Ssh),
}
