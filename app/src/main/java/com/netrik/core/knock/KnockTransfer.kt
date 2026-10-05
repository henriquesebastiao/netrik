package com.netrik.core.knock

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Export/import file of the saved knocks (JSON). Example:
 * ```
 * {"format": "netrik-knock", "version": 1, "groups": ["Servers"],
 *  "knocks": [{"name": "VPS", "host": "vps.example.com", "group": "Servers", "delay_ms": 500, "verify_port": 22,
 *              "steps": [{"protocol": "tcp", "port": 7000}, {"protocol": "icmp", "size": 64}]}]}
 * ```
 * Groups are matched by name; "group" missing or null = no group.
 */
object KnockTransfer {

    const val FORMAT = "netrik-knock"
    const val VERSION = 1

    /** Larger files aren't knock lists: refuse them before reading. */
    const val MAX_FILE_BYTES = 1_000_000

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Serializable
    private data class FileRoot(
        val format: String = "",
        val version: Int = 0,
        val groups: List<String> = emptyList(),
        val knocks: List<FileKnock> = emptyList(),
    )

    @Serializable
    private data class FileKnock(
        val name: String = "",
        val host: String = "",
        val group: String? = null,
        @SerialName("delay_ms") val delayMs: Int = KnockForm.DEFAULT_DELAY_MS,
        @SerialName("verify_port") val verifyPort: Int? = null,
        val steps: List<FileStep> = emptyList(),
    )

    @Serializable
    private data class FileStep(
        val protocol: String = "",
        val port: Int? = null,
        val size: Int? = null,
    )

    /** Exports [groups] (empty ones included) and [profiles]; a profile's group is written by name. */
    fun encode(groups: List<KnockGroup>, profiles: List<KnockProfile>): String {
        val names = groups.associate { it.id to it.name }
        val root = FileRoot(
            format = FORMAT,
            version = VERSION,
            groups = groups.map { it.name },
            knocks = profiles.map { profile ->
                FileKnock(
                    name = profile.name,
                    host = profile.host,
                    group = profile.groupId?.let(names::get),
                    delayMs = profile.delayMs,
                    verifyPort = profile.verifyPort,
                    steps = profile.steps.map { FileStep(it.protocol.storedName, it.port, it.payloadSize) },
                )
            },
        )
        return json.encodeToString(FileRoot.serializer(), root)
    }

    sealed interface Parsed {
        data class Ok(val file: KnockFile) : Parsed
        /** Not JSON, or JSON from something else. */
        data object NotKnockFile : Parsed
        /** Made by a newer Netrik. */
        data class NewerVersion(val version: Int) : Parsed
    }

    /** Knocks read from a file; [invalid] counts the entries that can't run (bad host, port, protocol...). */
    data class KnockFile(val groups: List<String>, val knocks: List<FileEntry>, val invalid: Int)

    data class FileEntry(val groupName: String?, val profile: KnockProfile)

    fun parse(text: String): Parsed {
        val root = try {
            json.decodeFromString(FileRoot.serializer(), text)
        } catch (_: SerializationException) {
            return Parsed.NotKnockFile
        } catch (_: IllegalArgumentException) {
            return Parsed.NotKnockFile
        }
        if (root.format != FORMAT) return Parsed.NotKnockFile
        if (root.version > VERSION) return Parsed.NewerVersion(root.version)
        if (root.version < 1) return Parsed.NotKnockFile
        var invalid = 0
        val entries = root.knocks.mapNotNull { knock ->
            val profile = knock.toProfile()
            if (profile == null || !KnockForm.isValid(profile)) {
                invalid++
                null
            } else {
                FileEntry(knock.group?.trim()?.takeIf { it.isNotEmpty() }, profile)
            }
        }
        return Parsed.Ok(KnockFile(root.groups.map { it.trim() }.filter { it.isNotEmpty() }, entries, invalid))
    }

    private fun FileKnock.toProfile(): KnockProfile? {
        val parsedSteps = steps.map { step ->
            val protocol = KnockProtocol.fromStored(step.protocol) ?: return null
            when (protocol) {
                KnockProtocol.Icmp -> KnockStep(protocol, payloadSize = step.size)
                else -> KnockStep(protocol, port = step.port)
            }
        }
        val host = host.trim()
        return KnockProfile(
            id = 0,
            name = KnockForm.displayName(name, host),
            host = host,
            groupId = null,
            delayMs = delayMs,
            verifyPort = verifyPort,
            steps = parsedSteps,
        )
    }

    /** What an import will do: groups to create, knocks to add and how many were skipped. */
    data class ImportPlan(
        val newGroups: List<String>,
        val knocks: List<FileEntry>,
        val duplicates: Int,
        val invalid: Int,
    )

    /**
     * Merges [file] into what's saved: groups with the same name (ignoring case) become one, and a knock
     * identical to a saved one (same name, host and steps) — or repeated in the file — is skipped.
     */
    fun plan(file: KnockFile, existingGroups: List<KnockGroup>, existing: List<KnockProfile>): ImportPlan {
        val known = existingGroups.map { it.name.trim().lowercase() }.toMutableSet()
        val newGroups = mutableListOf<String>()
        fun addGroup(name: String) {
            if (known.add(name.lowercase())) newGroups += name
        }
        file.groups.forEach(::addGroup)
        val accepted = mutableListOf<FileEntry>()
        var duplicates = 0
        file.knocks.forEach { entry ->
            val repeated = existing.any { it.sameKnockAs(entry.profile) } || accepted.any { it.profile.sameKnockAs(entry.profile) }
            if (repeated) {
                duplicates++
            } else {
                entry.groupName?.let(::addGroup)
                accepted += entry
            }
        }
        return ImportPlan(newGroups, accepted, duplicates, file.invalid)
    }
}
