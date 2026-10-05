package com.netrik.core.knock

import com.netrik.core.common.IoDispatcher
import com.netrik.core.database.KnockDao
import com.netrik.core.database.KnockProfileEntity
import com.netrik.core.database.KnockStepEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Import summary shown to the user. */
data class KnockImportResult(val imported: Int, val groupsCreated: Int, val duplicates: Int, val invalid: Int)

@Singleton
class KnockRepository @Inject constructor(
    private val dao: KnockDao,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    val groups: Flow<List<KnockGroup>> = dao.observeGroups().map { list -> list.map { KnockGroup(it.id, it.name, it.expanded) } }

    val profiles: Flow<List<KnockProfile>> = combine(dao.observeProfiles(), dao.observeSteps()) { profiles, steps ->
        val byProfile = steps.groupBy { it.profileId }
        profiles.map { it.toModel(byProfile[it.id].orEmpty()) }
    }

    suspend fun profile(id: Long): KnockProfile? = withContext(io) {
        dao.profile(id)?.let { it.toModel(dao.steps(id)) }
    }

    /** Saves (new when id is 0); returns the profile id. */
    suspend fun save(profile: KnockProfile): Long = withContext(io) {
        val createdAt = dao.profile(profile.id)?.createdAt ?: clock.millis()
        dao.saveProfile(profile.toEntity(createdAt), profile.steps.map { it.toEntity() })
    }

    suspend fun delete(id: Long) = withContext(io) { dao.deleteProfile(id) }

    suspend fun move(id: Long, groupId: Long?) = withContext(io) { dao.moveProfile(id, groupId) }

    suspend fun createGroup(name: String): Long = withContext(io) { dao.appendGroup(name) }

    suspend fun renameGroup(id: Long, name: String) = withContext(io) { dao.renameGroup(id, name) }

    suspend fun deleteGroup(id: Long) = withContext(io) { dao.deleteGroup(id) }

    suspend fun setExpanded(id: Long, expanded: Boolean) = withContext(io) { dao.setExpanded(id, expanded) }

    suspend fun setAllExpanded(expanded: Boolean) = withContext(io) { dao.setAllExpanded(expanded) }

    /** JSON with every knock, or only the ones of [groupId] (and that group) when given. */
    suspend fun export(groupId: Long?): String = withContext(io) {
        val groups = groups.first()
        val profiles = profiles.first()
        if (groupId == null) {
            KnockTransfer.encode(groups, profiles)
        } else {
            KnockTransfer.encode(groups.filter { it.id == groupId }, profiles.filter { it.groupId == groupId })
        }
    }

    /** Merges a parsed file into the saved knocks (see [KnockTransfer.plan]). */
    suspend fun import(file: KnockTransfer.KnockFile): KnockImportResult = withContext(io) {
        val plan = KnockTransfer.plan(file, groups.first(), profiles.first())
        val now = clock.millis()
        dao.importAll(
            newGroups = plan.newGroups,
            profiles = plan.knocks.map { entry -> entry.profile.toEntity(now) to entry.profile.steps.map { it.toEntity() } },
            groupOf = plan.knocks.map { it.groupName },
        )
        KnockImportResult(plan.knocks.size, plan.newGroups.size, plan.duplicates, plan.invalid)
    }

    private fun KnockProfileEntity.toModel(steps: List<KnockStepEntity>) = KnockProfile(
        id = id,
        name = name,
        host = host,
        groupId = groupId,
        delayMs = delayMs,
        verifyPort = verifyPort,
        steps = steps.sortedBy { it.position }.mapNotNull { step ->
            KnockProtocol.fromStored(step.protocol)?.let { KnockStep(it, step.port, step.payloadSize) }
        },
    )

    private fun KnockProfile.toEntity(createdAt: Long) = KnockProfileEntity(
        id = id,
        name = name,
        host = host,
        groupId = groupId,
        delayMs = delayMs,
        verifyPort = verifyPort,
        createdAt = createdAt,
    )

    private fun KnockStep.toEntity() = KnockStepEntity(
        profileId = 0,
        position = 0,
        protocol = protocol.storedName,
        port = port,
        payloadSize = payloadSize,
    )
}
