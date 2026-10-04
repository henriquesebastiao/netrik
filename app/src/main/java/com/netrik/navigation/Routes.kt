package com.netrik.navigation

import kotlinx.serialization.Serializable

@Serializable data object HubRoute
@Serializable data object DevicesRoute
@Serializable data object WifiRoute
@Serializable data object SshRoute

/**
 * Tool without its own tab, opened on top of the origin tab. [origin] keeps that tab
 * highlighted in the bar; [target] carries the target (IP/host) when opened from another screen.
 *
 * Tools without a screen yet use this route with a placeholder screen; each one gets a dedicated route once built.
 */
@Serializable
data class ToolRoute(
    val tool: NetrikTool,
    val origin: TopLevelDestination,
    val target: String? = null,
)

/** MAC/OUI lookup; [mac] prefills the field when opened from another screen. */
@Serializable
data class OuiRoute(
    val origin: TopLevelDestination,
    val mac: String? = null,
)

/** Ping; [target] prefills the target (e.g. quick action from a device's details). */
@Serializable
data class PingRoute(
    val origin: TopLevelDestination,
    val target: String? = null,
)

@Serializable
data class TracerouteRoute(
    val origin: TopLevelDestination,
    val target: String? = null,
)

/** Details of a device found by the scan (inside the Devices tab). */
@Serializable
data class DeviceDetailRoute(val ip: String)

/** Port Scanner; [target] prefills the host (e.g. quick action from a device's details). */
@Serializable
data class PortScanRoute(
    val origin: TopLevelDestination,
    val target: String? = null,
)

/**
 * Full-screen SSH form (without the navigation bar), inside the SSH tab: [hostId] edits a
 * saved host; [target] prefills the host of a new connection (e.g. quick action from a device).
 */
@Serializable
data class SshFormRoute(
    val hostId: Long? = null,
    val target: String? = null,
    val passwordRejected: Boolean = false,
)

/** Full-screen SSH terminal, with the tabs of the open sessions (inside the SSH tab). */
@Serializable data object SshTerminalRoute

/** Settings screen, opened from the Tools tab top bar. */
@Serializable data object SettingsRoute
