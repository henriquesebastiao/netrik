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
    fun `importa a base embarcada no primeiro uso`() = runTest {
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
    fun `o prefixo mais específico vence`() = runTest {
        val repo = repository(FakeOuiDao(), FakeSource())

        // 8C1F64 é MA-L do próprio IEEE, mas 8C1F64AFA é um MA-S de outra empresa.
        val ms = repo.lookup("8C1F64AFA123")!!
        assertEquals(OuiRegistry.MaS, ms.record.registry)
        assertEquals("DATA ELECTRONIC DEVICES, INC", ms.record.organization)

        val ml = repo.lookup("8C1F64000000")!!
        assertEquals(OuiRegistry.MaL, ml.record.registry)

        assertEquals(OuiRegistry.MaM, repo.lookup("C85CE27")?.record?.registry)
        assertNull(repo.lookup("AABBCC"))
    }

    @Test
    fun `consulta em lote`() = runTest {
        val repo = repository(FakeOuiDao(), FakeSource())
        val result = repo.lookupMany(listOf("3C22FB000001", "8C1F64AFA000", "DAA1196E035C"))
        assertEquals(setOf("3C22FB000001", "8C1F64AFA000"), result.keys)
        assertEquals(OuiRegistry.MaS, result.getValue("8C1F64AFA000").record.registry)
    }

    @Test
    fun `atualização do IEEE troca a base e conta os prefixos novos`() = runTest {
        val dao = FakeOuiDao()
        val downloaded = bundled.toMutableMap()
        downloaded[OuiRegistry.MaL] = downloaded.getValue(OuiRegistry.MaL) + "MA-L,001132,Synology Incorporated,Taipei TW\n"
        val repo = repository(dao, FakeSource(downloads = downloaded, minimumOk = true))
        repo.lookup("3C22FB")

        val event = nextEvent(repo)
        repo.updateFromIeee()
        advanceUntilIdle()

        // 5 prefixos reais + as linhas sintéticas de preenchimento, todas novas.
        assertEquals(OuiUpdateEvent.Success(total = 5 + PADDING, added = 1 + PADDING), event.await())
        assertEquals("ieee", dao.meta()?.source)
        assertEquals("2026-10-04", dao.meta()?.dataDate)
        assertEquals("Synology Incorporated", repo.lookup("001132")?.record?.organization)
    }

    @Test
    fun `falha no download mantém a base atual`() = runTest {
        val dao = FakeOuiDao()
        val repo = repository(dao, FakeSource(downloadError = IOException("sem rede")))
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
    fun `servidor que recusa o download é reportado`() = runTest {
        val repo = repository(FakeOuiDao(), FakeSource(downloadError = HttpStatusException(418)))
        val event = nextEvent(repo)
        repo.updateFromIeee()
        advanceUntilIdle()
        assertEquals(OuiUpdateEvent.Failure(OuiUpdateEvent.Failure.Reason.Rejected), event.await())
    }

    @Test
    fun `arquivo com poucos registros é rejeitado sem tocar na base`() = runTest {
        val dao = FakeOuiDao()
        // minimumOk = false: o repositório real exige dezenas de milhares de linhas.
        val repo = repository(dao, FakeSource(downloads = bundled, minimumOk = false))
        repo.lookup("3C22FB")

        val event = nextEvent(repo)
        repo.updateFromIeee()
        advanceUntilIdle()

        assertEquals(OuiUpdateEvent.Failure(OuiUpdateEvent.Failure.Reason.InvalidData), event.await())
        assertEquals("bundled", dao.meta()?.source)
    }

    @Test
    fun `reimporta quando o APK traz uma base mais nova`() = runTest {
        val dao = FakeOuiDao()
        dao.upsertMeta(OuiMetaEntity(source = "ieee", dataDate = "2025-01-01", prefixCount = 0))
        val repo = repository(dao, FakeSource())
        assertEquals("Apple, Inc.", repo.lookup("3C22FB")?.record?.organization)
        assertEquals("bundled", dao.meta()?.source)
    }

    @Test
    fun `histórico guarda a consulta e traz o fabricante atual`() = runTest {
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

    /** Começa a escutar antes de disparar a atualização, para não perder o evento. */
    private fun TestScope.nextEvent(repo: DefaultOuiRepository) =
        backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { repo.updateEvents.first() }

    private fun TestScope.repository(dao: FakeOuiDao, source: FakeSource) = DefaultOuiRepository(
        dao = dao,
        source = source,
        clock = clock,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        appScope = backgroundScope,
    )

    /** Fonte falsa: assets em memória e downloads gravados em arquivos temporários. */
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
            // Para simular um arquivo do tamanho real, repete linhas válidas até passar do mínimo.
            val padded = if (minimumOk) body + padding(registry) else body
            onProgress(padded.length.toLong(), padded.length.toLong())
            return File.createTempFile("oui-test", ".csv").apply {
                deleteOnExit()
                writeText(header + padded)
            }
        }

        /** Linhas extras com prefixos sintéticos (fora dos usados nos testes) só para passar a validação de tamanho. */
        private fun padding(registry: OuiRegistry): String = buildString {
            val count = if (registry == OuiRegistry.MaL) 30_000 else 3_000
            repeat(count) { i ->
                val prefix = "F" + i.toString(16).uppercase().padStart(registry.hexDigits - 1, '0')
                append("${registry.label},$prefix,Sintético $i,\n")
            }
        }
    }
}

/** DAO em memória com o mesmo contrato do Room usado pelo repositório. */
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
