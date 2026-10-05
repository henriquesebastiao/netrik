package com.netrik.feature.knock

import com.netrik.core.knock.KnockError
import com.netrik.core.knock.KnockField
import com.netrik.core.knock.KnockFieldError
import com.netrik.core.knock.KnockGroup
import com.netrik.core.knock.KnockImportResult
import com.netrik.core.knock.KnockProfile
import com.netrik.core.knock.KnockProtocol
import com.netrik.core.knock.KnockStepError
import com.netrik.core.knock.PortCheck

/** Group as it shows in the list; a null [id] is "No group". */
data class KnockGroupUi(
    val id: Long?,
    val name: String?,
    val expanded: Boolean,
    val knocks: List<KnockProfile>,
)

data class KnockListState(
    val loaded: Boolean = false,
    val groups: List<KnockGroupUi> = emptyList(),
    val allGroups: List<KnockGroup> = emptyList(),
    val anyExpanded: Boolean = true,
    val hasKnocks: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && allGroups.isEmpty() && !hasKnocks
}

object KnockList {
    /** All groups (empty ones too) and "No group" last, only when it has knocks. */
    fun build(groups: List<KnockGroup>, profiles: List<KnockProfile>, noGroupExpanded: Boolean): List<KnockGroupUi> {
        val byGroup = profiles.groupBy { profile -> profile.groupId?.takeIf { id -> groups.any { it.id == id } } }
        val named = groups.map { KnockGroupUi(it.id, it.name, it.expanded, byGroup[it.id].orEmpty()) }
        val loose = byGroup[null].orEmpty()
        return if (loose.isEmpty()) named else named + KnockGroupUi(null, null, noGroupExpanded, loose)
    }
}

sealed interface KnockDialog {
    data class GroupEdit(val groupId: Long?, val value: String) : KnockDialog
    data class GroupDelete(val groupId: Long, val name: String, val knockCount: Int) : KnockDialog
    data class KnockDelete(val id: Long, val name: String) : KnockDialog
    data class Move(val id: Long, val name: String, val groupId: Long?) : KnockDialog
    data class Imported(val result: KnockImportResult) : KnockDialog
    data class ImportFailed(val reason: ImportFailure) : KnockDialog
}

enum class ImportFailure { Unreadable, NotKnockFile, NewerVersion, Empty }

sealed interface KnockMessage {
    data class GroupCreated(val name: String) : KnockMessage
    data object GroupRenamed : KnockMessage
    data object GroupDeleted : KnockMessage
    data object KnockDeleted : KnockMessage
    data object KnockMoved : KnockMessage
    data class KnockDuplicated(val name: String) : KnockMessage
    data object Exported : KnockMessage
    data object ExportFailed : KnockMessage
}

enum class StepStatus { Pending, Sent, Failed }

enum class KnockPhase { Resolving, Running, Done, Stopped, Failed }

sealed interface KnockRunError {
    data object HostNotFound : KnockRunError
    data object LocalNetworkPermission : KnockRunError
    data class Step(val index: Int, val error: KnockError) : KnockRunError
}

/** Knock in progress or finished (bottom sheet). [verifyResult] null while testing [verifyPort]. */
data class KnockRunState(
    val profile: KnockProfile,
    val phase: KnockPhase = KnockPhase.Resolving,
    val address: String? = null,
    val steps: List<StepStatus> = List(profile.steps.size) { StepStatus.Pending },
    val error: KnockRunError? = null,
    val verifying: Boolean = false,
    val verifyResult: PortCheck? = null,
) {
    val running: Boolean get() = phase == KnockPhase.Resolving || phase == KnockPhase.Running
    /** Next step to go out, highlighted while running. */
    val currentStep: Int? get() = if (phase == KnockPhase.Running) steps.indexOfFirst { it == StepStatus.Pending }.takeIf { it >= 0 } else null
}

/** Step being edited; [key] keeps the row identity when reordering. [value]: port, or ICMP payload size. */
data class StepDraft(val key: Long, val protocol: KnockProtocol, val value: String)

data class KnockFormState(
    val loaded: Boolean = false,
    val editingId: Long? = null,
    val name: String = "",
    val host: String = "",
    val groupId: Long? = null,
    val delayMs: String = "",
    val verifyPort: String = "",
    val steps: List<StepDraft> = emptyList(),
    val errors: Map<KnockField, KnockFieldError> = emptyMap(),
    val stepErrors: Map<Int, KnockStepError> = emptyMap(),
) {
    val isNew: Boolean get() = editingId == null
}
