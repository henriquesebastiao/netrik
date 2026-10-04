package com.netrik.navigation

import kotlinx.serialization.Serializable

@Serializable data object HubRoute
@Serializable data object DevicesRoute
@Serializable data object WifiRoute
@Serializable data object SshRoute

/**
 * Ferramenta sem aba própria, aberta por cima da aba de origem. [origin] mantém essa aba
 * destacada na barra; [target] leva o alvo (IP/host) quando aberta a partir de outra tela.
 *
 * Ferramentas ainda sem tela usam esta rota com tela provisória; cada etapa troca a sua por uma rota dedicada.
 */
@Serializable
data class ToolRoute(
    val tool: NetrikTool,
    val origin: TopLevelDestination,
    val target: String? = null,
)

/** Consulta MAC/OUI; [mac] pré-preenche o campo quando aberta a partir de outra tela. */
@Serializable
data class OuiRoute(
    val origin: TopLevelDestination,
    val mac: String? = null,
)

/** Ping; [target] pré-preenche o alvo (ex.: ação rápida dos detalhes de um dispositivo). */
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

/** Detalhes de um dispositivo encontrado na varredura (dentro da aba Dispositivos). */
@Serializable
data class DeviceDetailRoute(val ip: String)

/** Port Scanner; [target] pré-preenche o host (ex.: ação rápida dos detalhes de um dispositivo). */
@Serializable
data class PortScanRoute(
    val origin: TopLevelDestination,
    val target: String? = null,
)

/**
 * Formulário SSH em tela cheia (sem a barra de navegação), dentro da aba SSH: [hostId] edita um
 * host salvo; [target] pré-preenche o host de uma conexão nova (ex.: ação rápida de um dispositivo).
 */
@Serializable
data class SshFormRoute(
    val hostId: Long? = null,
    val target: String? = null,
    val passwordRejected: Boolean = false,
)
