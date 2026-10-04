package com.netrik.feature.oui

import com.netrik.core.oui.MacInput
import com.netrik.core.oui.OuiDbStatus
import com.netrik.core.oui.OuiMatch

data class OuiUiState(
    val input: String = "",
    val parsed: MacInput = MacInput.Empty,
    /** Shows the "too few digits" error only after the user tries to look up. */
    val showShortError: Boolean = false,
    val result: OuiResult? = null,
    val history: List<HistoryItem> = emptyList(),
    /** Null while the offline database is prepared on first use. */
    val db: OuiDbStatus? = null,
    val now: Long = 0,
)

sealed interface OuiResult {
    val hex: String

    data class Loading(override val hex: String) : OuiResult

    /**
     * [match] may exist even with [locallyAdministered], but in practice the IEEE doesn't assign
     * prefixes with the U/L bit set.
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
