package com.netrik.core.oui

import com.netrik.core.common.ApplicationScope
import com.netrik.core.common.IoDispatcher
import com.netrik.core.database.OuiDao
import com.netrik.core.database.OuiHistoryEntity
import com.netrik.core.database.OuiMetaEntity
import com.netrik.core.database.toEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class OuiDbStatus(
    val prefixCount: Int?,
    val dataDate: LocalDate?,
    val downloadedFromIeee: Boolean,
    val update: OuiUpdateState,
)

sealed interface OuiUpdateState {
    data object Idle : OuiUpdateState
    /** Importing the bundled database on first use. */
    data object Preparing : OuiUpdateState
    data class Downloading(val registry: OuiRegistry, val step: Int, val steps: Int, val fraction: Float?) : OuiUpdateState
    data object Installing : OuiUpdateState
}

sealed interface OuiUpdateEvent {
    data class Success(val total: Int, val added: Int) : OuiUpdateEvent
    data class Failure(val reason: Reason) : OuiUpdateEvent {
        enum class Reason { Network, Rejected, InvalidData }
    }
}

data class OuiHistoryEntry(val hex: String, val queriedAt: Long, val match: OuiMatch?)

/** OUI database shared by the MAC lookup and the LAN and Wi-Fi scanners. */
interface OuiRepository {
    val status: Flow<OuiDbStatus>
    val updateEvents: SharedFlow<OuiUpdateEvent>
    val history: Flow<List<OuiHistoryEntry>>

    /** Most specific registration (MA-S > MA-M > MA-L) for a MAC or prefix in hexadecimal. */
    suspend fun lookup(hex: String): OuiMatch?

    /** Batch lookup; the key is the given hexadecimal. Missing = no registration. */
    suspend fun lookupMany(hexes: Collection<String>): Map<String, OuiMatch>

    /** Downloads the three IEEE registries and swaps the database if all are valid. Ignored if already running. */
    fun updateFromIeee()

    suspend fun recordQuery(hex: String)
    suspend fun removeFromHistory(hex: String)
    suspend fun clearHistory()
}

@Singleton
class DefaultOuiRepository @Inject constructor(
    private val dao: OuiDao,
    private val source: OuiSource,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : OuiRepository {

    private val updateState = MutableStateFlow<OuiUpdateState>(OuiUpdateState.Idle)
    private val _updateEvents = MutableSharedFlow<OuiUpdateEvent>(extraBufferCapacity = 1)
    override val updateEvents: SharedFlow<OuiUpdateEvent> = _updateEvents

    private val loadLock = Mutex()
    @Volatile private var loaded = false
    private val updateLock = Mutex()

    override val status: Flow<OuiDbStatus> =
        combine(dao.observeMeta(), updateState) { meta, update ->
            OuiDbStatus(
                prefixCount = meta?.prefixCount,
                dataDate = meta?.dataDate?.let(LocalDate::parse),
                downloadedFromIeee = meta?.source == SOURCE_IEEE,
                update = update,
            )
        }.onStart { ensureLoaded() }

    override val history: Flow<List<OuiHistoryEntry>> =
        dao.observeHistory(HISTORY_LIMIT).map { entries ->
            val matches = lookupMany(entries.map { it.hex })
            entries.map { OuiHistoryEntry(it.hex, it.queriedAt, matches[it.hex]) }
        }

    override suspend fun lookup(hex: String): OuiMatch? {
        ensureLoaded()
        val candidates = OuiRegistry.candidatePrefixes(hex)
        if (candidates.isEmpty()) return null
        return dao.findBestMatch(candidates)?.toRecord()?.let { OuiMatch(it, hex) }
    }

    override suspend fun lookupMany(hexes: Collection<String>): Map<String, OuiMatch> {
        if (hexes.isEmpty()) return emptyMap()
        ensureLoaded()
        val records = hexes.flatMap(OuiRegistry::candidatePrefixes).distinct()
            .chunked(SQLITE_MAX_ARGS)
            .flatMap { dao.findAll(it) }
            .mapNotNull { it.toRecord() }
            .associateBy { it.prefix }
        return hexes.mapNotNull { hex ->
            OuiRegistry.candidatePrefixes(hex).firstNotNullOfOrNull(records::get)?.let { hex to OuiMatch(it, hex) }
        }.toMap()
    }

    override fun updateFromIeee() {
        if (!updateLock.tryLock()) return
        appScope.launch {
            try {
                runUpdate()
            } finally {
                updateLock.unlock()
            }
        }
    }

    override suspend fun recordQuery(hex: String) {
        dao.upsertHistory(OuiHistoryEntity(hex, clock.millis()))
        dao.trimHistory(HISTORY_LIMIT)
    }

    override suspend fun removeFromHistory(hex: String) = dao.deleteHistory(hex)

    override suspend fun clearHistory() = dao.clearHistory()

    /** Imports the bundled database on first use, or when the APK carries newer data than the installed one. */
    private suspend fun ensureLoaded() {
        if (!loaded) loadLock.withLock { if (!loaded) loadBundledIfNeeded() }
    }

    private suspend fun loadBundledIfNeeded() {
        val bundledVersion = withContext(ioDispatcher) { source.bundledVersion() }
        val installed = dao.meta()
        if (installed == null || installed.dataDate < bundledVersion) {
            importBundled(bundledVersion)
        }
        loaded = true
    }

    private suspend fun importBundled(bundledVersion: String) {
        updateState.value = OuiUpdateState.Preparing
        try {
            val records = withContext(ioDispatcher) {
                OuiRegistry.entries.flatMap { registry ->
                    source.openBundled(registry).use { OuiCsvParser.parse(it, registry) }
                }
            }
            install(records, SOURCE_BUNDLED, bundledVersion)
        } finally {
            updateState.value = OuiUpdateState.Idle
        }
    }

    private suspend fun runUpdate() {
        val files = mutableListOf<File>()
        try {
            ensureLoaded()
            val records = mutableListOf<OuiRecord>()
            OuiRegistry.entries.forEachIndexed { index, registry ->
                updateState.value = OuiUpdateState.Downloading(registry, index + 1, OuiRegistry.entries.size, null)
                // runInterruptible: cancelling the coroutine interrupts the blocking network read.
                val file = runInterruptible(ioDispatcher) {
                    source.download(registry) { read, total ->
                        val fraction = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else null
                        updateState.value = OuiUpdateState.Downloading(registry, index + 1, OuiRegistry.entries.size, fraction)
                    }
                }
                files += file
                currentCoroutineContext().ensureActive()
                val parsed = withContext(ioDispatcher) { file.bufferedReader().use { OuiCsvParser.parse(it, registry) } }
                if (parsed.size < registry.minimumRecords) throw OuiFormatException("Poucos registros em ${registry.label}")
                records += parsed
            }
            updateState.value = OuiUpdateState.Installing
            val before = dao.allPrefixes().toHashSet()
            install(records, SOURCE_IEEE, LocalDate.now(clock).toString())
            _updateEvents.emit(OuiUpdateEvent.Success(total = records.size, added = records.count { it.prefix !in before }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpStatusException) {
            _updateEvents.emit(OuiUpdateEvent.Failure(OuiUpdateEvent.Failure.Reason.Rejected))
        } catch (e: OuiFormatException) {
            _updateEvents.emit(OuiUpdateEvent.Failure(OuiUpdateEvent.Failure.Reason.InvalidData))
        } catch (e: IOException) {
            _updateEvents.emit(OuiUpdateEvent.Failure(OuiUpdateEvent.Failure.Reason.Network))
        } finally {
            files.forEach { it.delete() }
            updateState.value = OuiUpdateState.Idle
        }
    }

    private suspend fun install(records: List<OuiRecord>, sourceName: String, dataDate: String) {
        val entities = records.distinctBy { it.prefix }.map { it.toEntity() }
        dao.replaceAll(entities, OuiMetaEntity(source = sourceName, dataDate = dataDate, prefixCount = entities.size))
    }

    private val OuiRegistry.minimumRecords: Int
        get() = when (this) {
            OuiRegistry.MaL -> 30_000
            OuiRegistry.MaM -> 3_000
            OuiRegistry.MaS -> 3_000
        }

    private companion object {
        const val SOURCE_BUNDLED = "bundled"
        const val SOURCE_IEEE = "ieee"
        const val HISTORY_LIMIT = 20
        const val SQLITE_MAX_ARGS = 900
    }
}
