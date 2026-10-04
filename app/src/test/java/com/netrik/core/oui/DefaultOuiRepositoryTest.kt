package com.netrik.core.oui

import com.netrik.core.database.OuiDao
import com.netrik.core.database.OuiHistoryEntity
import com.netrik.core.database.OuiMetaEntity
import com.netrik.core.database.OuiPrefixEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.Reader
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val PADDING = 30_000 + 3_000 + 3_000

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultOuiRepositoryTest {

    private val header = "Registry,Assignment,Organization Name,Organization Address\n"
    private val clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)

    private val bundled = mapOf(
        OuiRegistry.MaL to "MA-L,3C22FB,\"Apple, Inc.\",Cupertino US\nMA-L,8C1F64,IEEE Registration Authority,Piscataway US\n",
        OuiRegistry.MaM to "MA-M,C85CE27,SYNERGY SYSTEMS AND SOLUTIONS,Faridabad IN\n",
        OuiRegistry.MaS to "MA-S,8C1F64AFA,\"DATA ELECTRONIC DEVICES, INC\",Salem NH US\n",
    )

    @Test
    fun `imports the bundled database on first use`() = runTest {
        val dao = FakeOuiDao()
        val repo = repository(dao, FakeSource())

        assertNull(dao.meta())
        assertEquals("Apple, Inc.", repo.lookup("3C22FB9A107E")?.record?.organization)

        val meta = dao.meta()!!
        assertEquals("bundled", meta.source)
        assertEquals("2026-10-03", meta.dataDate)
        assertEquals(4, meta.prefixCount)
    }

    @Test
    fun `the most specific prefix wins`() = runTest {
        val repo = repository(FakeOuiDao(), FakeSource())

        // 8C1F64 is the IEEE's own MA-L, but 8C1F64AFA is an MA-S of another company.
        val ms = repo.lookup("8C1F64AFA123")!!
        assertEquals(OuiRegistry.MaS, ms.record.registry)
        assertEquals("DATA ELECTRONIC DEVICES, INC", ms.record.organization)

        val ml = repo.lookup("8C1F64000000")!!
        assertEquals(OuiRegistry.MaL, ml.record.registry)

        assertEquals(OuiRegistry.MaM, repo.lookup("C85CE27")?.record?.registry)
        assertNull(repo.lookup("AABBCC"))
    }

    @Test
    fun `batch lookup`() = runTest {
        val repo = repository(FakeOuiDao(), FakeSource())
        val result = repo.lookupMany(listOf("3C22FB000001", "8C1F64AFA000", "DAA1196E035C"))
        assertEquals(setOf("3C22FB000001", "8C1F64AFA000"), result.keys)
        assertEquals(OuiRegistry.MaS, result.getValue("8C1F64AFA000").record.registry)
    }

    @Test
    fun `IEEE update swaps the database and counts the new prefixes`() = runTest {
        val dao = FakeOuiDao()
        val downloaded = bundled.toMutableMap()
        downloaded[OuiRegistry.MaL] = downloaded.getValue(OuiRegistry.MaL) + "MA-L,001132,Synology Incorporated,Taipei TW\n"
        val repo = repository(dao, FakeSource(downloads = downloaded, minimumOk = true))
        repo.lookup("3C22FB")

        val event = nextEvent(repo)
        repo.updateFromIeee()
        advanceUntilIdle()

        // 5 real prefixes + the synthetic padding lines, all new.
        assertEquals(OuiUpdateEvent.Success(total = 5 + PADDING, added = 1 + PADDING), event.await())
        assertEquals("ieee", dao.meta()?.source)
        assertEquals("2026-10-04", dao.meta()?.dataDate)
        assertEquals("Synology Incorporated", repo.lookup("001132")?.record?.organization)
    }

    @Test
    fun `download failure keeps the current database`() = runTest {
        val dao = FakeOuiDao()
        val repo = repository(dao, FakeSource(downloadError = IOException("no network")))
        repo.lookup("3C22FB")
        val before = dao.meta()

        val event = nextEvent(repo)
        repo.updateFromIeee()
        advanceUntilIdle()

        assertEquals(OuiUpdateEvent.Failure(OuiUpdateEvent.Failure.Reason.Network), event.await())
        assertEquals(before, dao.meta())
        assertEquals("Apple, Inc.", repo.lookup("3C22FB")?.record?.organization)
    }

    @Test
    fun `server refusing the download is reported`() = runTest {
        val repo = repository(FakeOuiDao(), FakeSource(downloadError = HttpStatusException(418)))
        val event = nextEvent(repo)
        repo.updateFromIeee()
        advanceUntilIdle()
        assertEquals(OuiUpdateEvent.Failure(OuiUpdateEvent.Failure.Reason.Rejected), event.await())
    }

    @Test
    fun `file with too few records is rejected without touching the database`() = runTest {
        val dao = FakeOuiDao()
        // minimumOk = false: the real repository requires tens of thousands of lines.
        val repo = repository(dao, FakeSource(downloads = bundled, minimumOk = false))
        repo.lookup("3C22FB")

        val event = nextEvent(repo)
        repo.updateFromIeee()
        advanceUntilIdle()

        assertEquals(OuiUpdateEvent.Failure(OuiUpdateEvent.Failure.Reason.InvalidData), event.await())
        assertEquals("bundled", dao.meta()?.source)
    }

    @Test
    fun `reimports when the APK carries a newer database`() = runTest {
        val dao = FakeOuiDao()
        dao.upsertMeta(OuiMetaEntity(source = "ieee", dataDate = "2025-01-01", prefixCount = 0))
        val repo = repository(dao, FakeSource())
        assertEquals("Apple, Inc.", repo.lookup("3C22FB")?.record?.organization)
        assertEquals("bundled", dao.meta()?.source)
    }

    @Test
    fun `history keeps the lookup and brings the current vendor`() = runTest {
        val repo = repository(FakeOuiDao(), FakeSource())
        repo.recordQuery("3C22FB9A107E")
        repo.recordQuery("DAA1196E035C")

        val history = repo.history.first()
        assertEquals(listOf("DAA1196E035C", "3C22FB9A107E").toSet(), history.map { it.hex }.toSet())
        assertEquals("Apple, Inc.", history.first { it.hex == "3C22FB9A107E" }.match?.record?.organization)
        assertNull(history.first { it.hex == "DAA1196E035C" }.match)

        repo.removeFromHistory("DAA1196E035C")
        assertFalse(repo.history.first().any { it.hex == "DAA1196E035C" })
        repo.clearHistory()
        assertTrue(repo.history.first().isEmpty())
    }

    /** Starts listening before triggering the update, so the event isn't missed. */
    private fun TestScope.nextEvent(repo: DefaultOuiRepository) =
        backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { repo.updateEvents.first() }

    private fun TestScope.repository(dao: FakeOuiDao, source: FakeSource) = DefaultOuiRepository(
        dao = dao,
        source = source,
        clock = clock,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        appScope = backgroundScope,
    )

    /** Fake source: in-memory assets and downloads written to temporary files. */
    private inner class FakeSource(
        private val downloads: Map<OuiRegistry, String> = bundled,
        private val minimumOk: Boolean = true,
        private val downloadError: IOException? = null,
    ) : OuiSource {
        override fun bundledVersion() = "2026-10-03"

        override fun openBundled(registry: OuiRegistry): Reader = (header + bundled.getValue(registry)).reader()

        override fun download(registry: OuiRegistry, onProgress: (Long, Long) -> Unit): File {
            downloadError?.let { throw it }
            val body = downloads.getValue(registry)
            // To fake a real-size file, repeats valid lines until it passes the minimum.
            val padded = if (minimumOk) body + padding(registry) else body
            onProgress(padded.length.toLong(), padded.length.toLong())
            return File.createTempFile("oui-test", ".csv").apply {
                deleteOnExit()
                writeText(header + padded)
            }
        }

        /** Extra lines with synthetic prefixes (outside the ones used in the tests) just to pass the size check. */
        private fun padding(registry: OuiRegistry): String = buildString {
            val count = if (registry == OuiRegistry.MaL) 30_000 else 3_000
            repeat(count) { i ->
                val prefix = "F" + i.toString(16).uppercase().padStart(registry.hexDigits - 1, '0')
                append("${registry.label},$prefix,Synthetic $i,\n")
            }
        }
    }
}

/** In-memory DAO with the same contract as the Room DAO used by the repository. */
private class FakeOuiDao : OuiDao {
    private val prefixes = MutableStateFlow<Map<String, OuiPrefixEntity>>(emptyMap())
    private val metaFlow = MutableStateFlow<OuiMetaEntity?>(null)
    private val historyFlow = MutableStateFlow<List<OuiHistoryEntity>>(emptyList())

    override suspend fun findBestMatch(candidates: List<String>) =
        candidates.mapNotNull { prefixes.value[it] }.maxByOrNull { it.prefix.length }

    override suspend fun findAll(prefixes: List<String>) = prefixes.mapNotNull { this.prefixes.value[it] }
    override suspend fun allPrefixes() = prefixes.value.keys.toList()
    override fun observeMeta(): Flow<OuiMetaEntity?> = metaFlow
    override suspend fun meta() = metaFlow.value
    override suspend fun clearPrefixes() { prefixes.value = emptyMap() }
    override suspend fun insertPrefixes(prefixes: List<OuiPrefixEntity>) {
        this.prefixes.value = this.prefixes.value + prefixes.associateBy { it.prefix }
    }
    override suspend fun upsertMeta(meta: OuiMetaEntity) { metaFlow.value = meta }

    override fun observeHistory(limit: Int): Flow<List<OuiHistoryEntity>> =
        historyFlow.map { list -> list.sortedByDescending { it.queriedAt }.take(limit) }

    override suspend fun upsertHistory(entry: OuiHistoryEntity) {
        historyFlow.value = historyFlow.value.filter { it.hex != entry.hex } + entry
    }

    override suspend fun trimHistory(keep: Int) {
        historyFlow.value = historyFlow.value.sortedByDescending { it.queriedAt }.take(keep)
    }

    override suspend fun deleteHistory(hex: String) { historyFlow.value = historyFlow.value.filter { it.hex != hex } }
    override suspend fun clearHistory() { historyFlow.value = emptyList() }
}
