package com.netrik.feature.oui

import com.netrik.core.oui.MacInput
import com.netrik.core.oui.OuiDbStatus
import com.netrik.core.oui.OuiMatch

data class OuiUiState(
    val input: String = "",
    val parsed: MacInput = MacInput.Empty,
    /** Mostra o erro de "poucos dígitos" só depois de o usuário tentar consultar. */
    val showShortError: Boolean = false,
    val result: OuiResult? = null,
    val history: List<HistoryItem> = emptyList(),
    /** Null enquanto a base offline é preparada no primeiro uso. */
    val db: OuiDbStatus? = null,
    val now: Long = 0,
)

sealed interface OuiResult {
    val hex: String

    data class Loading(override val hex: String) : OuiResult

    /**
     * [match] pode existir mesmo com [locallyAdministered], mas na prática o IEEE não atribui
     * prefixos com o bit U/L ligado.
     */
    data class Done(
        override val hex: String,
        val match: OuiMatch?,
        val locallyAdministered: Boolean,
        val multicast: Boolean,
    ) : OuiResult
}

data class HistoryItem(
    val hex: String,
    val organization: String?,
    val locallyAdministered: Boolean,
    val queriedAt: Long,
)

sealed interface OuiMessage {
    data class Updated(val total: Int, val added: Int) : OuiMessage
    data object UpdateNetworkError : OuiMessage
    data object UpdateRejected : OuiMessage
    data object UpdateInvalid : OuiMessage
    data object HistoryCleared : OuiMessage
}
